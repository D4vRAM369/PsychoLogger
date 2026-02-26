package com.d4vram.psychologger

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * BackupManager - Gestión completa de backups automáticos y manuales
 *
 * FUNCIONALIDADES:
 * 1. Auto-backup cada 12 horas (datos + audios)
 * 2. Backup manual desde Ajustes
 * 3. Exportar ZIP de audios con cifrado AES-256 opcional
 * 4. Rotación automática (mantener últimos 7 backups)
 * 5. Restauración de backups
 *
 * CONCEPTOS CLAVE:
 * - ZIP: Compresión de múltiples archivos
 * - AES-256-GCM: Cifrado autenticado
 * - PBKDF2: Derivación de clave desde contraseña
 * - JSON: Serialización de datos
 */
class BackupManager(private val context: Context) {

    companion object {
        private const val TAG = "BackupManager"
        private const val MAX_BACKUPS = 7  // Mantener últimos 7 backups

        // Cifrado
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
    }

    /**
     * Directorios de backups
     */
    private val backupsDir: File by lazy {
        File(context.filesDir, "backups").apply {
            if (!exists()) mkdirs()
        }
    }

    private val audioNotesDir: File by lazy {
        File(context.filesDir, "audio_notes")
    }

    /**
     * Crear backup completo (automático o manual)
     *
     * ESTRUCTURA DEL BACKUP:
     * backups/
     *   ├── backup_2025-01-15_14-30-00.zip
     *   │   ├── data.json (localStorage completo)
     *   │   └── audios/
     *   │       ├── audio_uuid1.m4a
     *   │       └── audio_uuid2.m4a
     *
     * @return File del backup creado o null si falla
     */
    fun createBackup(): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val backupFile = File(backupsDir, "backup_$timestamp.zip")

            Log.d(TAG, "Creando backup: ${backupFile.name}")

            // Crear archivo ZIP
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->

                // 1. Añadir data.json con localStorage completo
                addLocalStorageToZip(zipOut)

                // 2. Añadir todos los archivos de audio
                addAudioFilesToZip(zipOut)
            }

            Log.d(TAG, "Backup creado exitosamente: ${backupFile.absolutePath}")

            // Limpiar backups antiguos
            cleanOldBackups()

            backupFile

        } catch (e: Exception) {
            Log.e(TAG, "Error al crear backup", e)
            null
        }
    }

    /**
     * Añade el contenido de localStorage al ZIP como data.json
     *
     * CONCEPTO: SharedPreferences vs localStorage
     * - localStorage del WebView no es accesible directamente desde Kotlin
     * - Solución: JavaScript envía los datos a Android vía bridge
     * - Por ahora creamos un placeholder, luego lo pasaremos desde JS
     */
    private fun addLocalStorageToZip(zipOut: ZipOutputStream) {
        // Este método será llamado con los datos desde JavaScript
        // Por ahora añadimos un placeholder
        val dataJson = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("version", "1.0")
            put("note", "Los datos reales se añadirán desde JavaScript")
        }

        val entry = ZipEntry("data.json")
        zipOut.putNextEntry(entry)
        zipOut.write(dataJson.toString(2).toByteArray())
        zipOut.closeEntry()
    }

    /**
     * Añade todos los archivos de audio al ZIP
     *
     * CONCEPTO: Iterar archivos y añadirlos a ZIP
     * ZipEntry representa un archivo/carpeta dentro del ZIP
     */
    private fun addAudioFilesToZip(zipOut: ZipOutputStream) {
        if (!audioNotesDir.exists()) {
            Log.d(TAG, "No hay carpeta de audios")
            return
        }

        val audioFiles = audioNotesDir.listFiles()?.filter { it.extension == "m4a" } ?: emptyList()
        Log.d(TAG, "Añadiendo ${audioFiles.size} archivos de audio al backup")

        audioFiles.forEach { audioFile ->
            try {
                val entry = ZipEntry("audios/${audioFile.name}")
                zipOut.putNextEntry(entry)

                FileInputStream(audioFile).use { fileIn ->
                    fileIn.copyTo(zipOut)
                }

                zipOut.closeEntry()
            } catch (e: Exception) {
                Log.e(TAG, "Error al añadir audio ${audioFile.name}", e)
            }
        }
    }

    /**
     * Crear backup con datos de localStorage (llamado desde JavaScript)
     *
     * @param localStorageData JSON string con todo el localStorage
     * @return File del backup o null
     */
    fun createBackupWithData(localStorageData: String): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val backupFile = File(backupsDir, "backup_$timestamp.zip")

            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->

                // 1. Añadir data.json con los datos reales
                val entry = ZipEntry("data.json")
                zipOut.putNextEntry(entry)
                zipOut.write(localStorageData.toByteArray())
                zipOut.closeEntry()

                // 2. Añadir audios
                addAudioFilesToZip(zipOut)
            }

            cleanOldBackups()
            backupFile

        } catch (e: Exception) {
            Log.e(TAG, "Error al crear backup con datos", e)
            null
        }
    }

    /**
     * Exportar solo los audios en un ZIP (con cifrado opcional)
     *
     * @param password Contraseña para cifrar (null = sin cifrar)
     * @return File del ZIP creado
     *
     * CONCEPTO: Cifrado de archivos ZIP
     * - Sin cifrado: ZIP normal
     * - Con cifrado: AES-256-GCM con PBKDF2
     */
    fun exportAudioZip(password: String? = null): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val zipName = if (password != null) {
                "audios_encrypted_$timestamp.zip"
            } else {
                "audios_$timestamp.zip"
            }

            val exportFile = File(context.cacheDir, zipName)

            if (password != null) {
                // ZIP con cifrado AES-256
                createEncryptedAudioZip(exportFile, password)
            } else {
                // ZIP normal sin cifrar
                createPlainAudioZip(exportFile)
            }

            exportFile

        } catch (e: Exception) {
            Log.e(TAG, "Error al exportar audios", e)
            null
        }
    }

    /**
     * Crear ZIP de audios SIN cifrar
     */
    private fun createPlainAudioZip(outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
            addAudioFilesToZip(zipOut)
        }
    }

    /**
     * Crear ZIP de audios CON cifrado AES-256
     *
     * ESTRUCTURA:
     * audios_encrypted.zip
     *   ├── metadata.json (salt, iv, algoritmo)
     *   └── data.enc (audios cifrados en ZIP interno)
     *
     * CONCEPTO: Nested ZIP
     * 1. Crear ZIP interno con los audios
     * 2. Cifrar ese ZIP completo
     * 3. Meter el cifrado en un ZIP externo con metadata
     */
    private fun createEncryptedAudioZip(outputFile: File, password: String) {
        // 1. Generar salt e IV
        val salt = generateRandomBytes(SALT_LENGTH)
        val iv = generateRandomBytes(IV_LENGTH)

        // 2. Derivar clave de la contraseña
        val key = deriveKey(password, salt)

        // 3. Crear ZIP temporal con los audios
        val tempZip = File(context.cacheDir, "temp_audios.zip")
        createPlainAudioZip(tempZip)

        // 4. Cifrar el ZIP temporal
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val encryptedData = File(context.cacheDir, "data.enc")
        CipherOutputStream(FileOutputStream(encryptedData), cipher).use { cipherOut ->
            FileInputStream(tempZip).use { it.copyTo(cipherOut) }
        }

        // 5. Crear ZIP final con metadata + datos cifrados
        ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
            // Metadata
            val metadata = JSONObject().apply {
                put("algorithm", "AES-256-GCM")
                put("salt", bytesToHex(salt))
                put("iv", bytesToHex(iv))
                put("iterations", PBKDF2_ITERATIONS)
                put("timestamp", System.currentTimeMillis())
            }

            zipOut.putNextEntry(ZipEntry("metadata.json"))
            zipOut.write(metadata.toString(2).toByteArray())
            zipOut.closeEntry()

            // Datos cifrados
            zipOut.putNextEntry(ZipEntry("data.enc"))
            FileInputStream(encryptedData).use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }

        // Limpiar archivos temporales
        tempZip.delete()
        encryptedData.delete()
    }

    /**
     * Derivar clave AES desde contraseña usando PBKDF2
     *
     * CONCEPTO: Key Derivation Function (KDF)
     * - PBKDF2: Password-Based Key Derivation Function 2
     * - Convierte contraseña débil en clave fuerte
     * - 120,000 iteraciones = protección contra fuerza bruta
     * - Salt único evita rainbow tables
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    /**
     * Limpiar backups antiguos (mantener solo los últimos MAX_BACKUPS)
     *
     * CONCEPTO: Rotación de archivos
     * - Ordenar por fecha (más antiguo primero)
     * - Eliminar excedentes
     */
    private fun cleanOldBackups() {
        val backups = backupsDir.listFiles()
            ?.filter { it.name.startsWith("backup_") && it.extension == "zip" }
            ?.sortedBy { it.lastModified() }  // Más antiguo primero
            ?: return

        val toDelete = backups.take((backups.size - MAX_BACKUPS).coerceAtLeast(0))
        toDelete.forEach { file ->
            Log.d(TAG, "Eliminando backup antiguo: ${file.name}")
            file.delete()
        }
    }

    /**
     * Listar todos los backups disponibles
     *
     * @return Lista de BackupInfo ordenada por fecha (más reciente primero)
     */
    fun listBackups(): List<BackupInfo> {
        return backupsDir.listFiles()
            ?.filter { it.name.startsWith("backup_") && it.extension == "zip" }
            ?.map { file ->
                BackupInfo(
                    file = file,
                    timestamp = file.lastModified(),
                    size = file.length()
                )
            }
            ?.sortedByDescending { it.timestamp }  // Más reciente primero
            ?: emptyList()
    }

    /**
     * Generar bytes aleatorios para salt/IV
     *
     * CONCEPTO: SecureRandom
     * - Generador criptográficamente seguro
     * - No usar Random() normal para criptografía
     */
    private fun generateRandomBytes(length: Int): ByteArray {
        return ByteArray(length).apply {
            java.security.SecureRandom().nextBytes(this)
        }
    }

    /**
     * Convertir bytes a hexadecimal (para serializar salt/iv)
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Data class con información de un backup
     */
    data class BackupInfo(
        val file: File,
        val timestamp: Long,
        val size: Long
    ) {
        val formattedDate: String
            get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

        val formattedSize: String
            get() = when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> "%.2f MB".format(size / (1024.0 * 1024.0))
            }
    }
}
