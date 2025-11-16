package com.d4vram.psychologger

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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

    private val backupPrefs by lazy {
        context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    }

    /**
     * Directorios de backups
     */
    private val backupsDir: File by lazy {
        val external = context.getExternalFilesDir("backups")?.apply {
            if (!exists()) mkdirs()
        }

        external?.takeIf { it.exists() && it.canWrite() } ?: File(context.filesDir, "backups").apply {
            if (!exists()) mkdirs()
        }
    }

    private val audioNotesDir: File by lazy {
        File(context.filesDir, "audio_notes")
    }

    private val photoDir: File by lazy {
        val external = context.getExternalFilesDir("entry_photos")?.apply {
            if (!exists()) mkdirs()
        }

        external ?: File(context.filesDir, "entry_photos").apply {
            if (!exists()) mkdirs()
        }
    }

    private val backupCacheDir: File by lazy {
        File(context.filesDir, "backup_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    private val localStorageSnapshotFile: File
        get() = File(backupCacheDir, "local_storage_snapshot.json")

    /**
     * Crear backup completo (automático o manual)
     *
     * ESTRUCTURA DEL BACKUP:
     * backups/
     *   ├── backup_2025-01-15_14-30-00.zip
     *   │   ├── data.json   (snapshot completo en JSON)
     *   │   ├── data.csv    (exportación tabular en CSV)
     *   │   ├── audios/
     *   │   │   ├── audio_uuid1.m4a
     *   │   │   └── audio_uuid2.m4a
     *   │   └── photos/
     *   │       ├── photo_uuid1.jpg
     *   │       └── photo_uuid2.jpg
     *
     * @return File del backup creado o null si falla
     */
    fun createBackup(): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val backupFile = File(backupsDir, "backup_$timestamp.zip")
            val snapshot = readLocalStorageSnapshot()

            Log.d(TAG, "Creando backup: ${backupFile.name}")

            // Crear archivo ZIP
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->

                // 1. Añadir data.json con localStorage completo
                addLocalStorageToZip(zipOut, snapshot)

                // 2. Añadir data.csv generado a partir del snapshot
                addCsvExportToZip(zipOut, snapshot)

                // 3. Añadir todos los archivos de audio
                addAudioFilesToZip(zipOut)

                // 4. Añadir fotos asociadas a registros
                addPhotoFilesToZip(zipOut)
            }

            Log.d(TAG, "Backup creado exitosamente: ${backupFile.absolutePath}")

            // Limpiar backups antiguos
            cleanOldBackups()

            updateLastBackupMetadata(backupFile, type = "auto")

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
     * - En backups automáticos se usa el snapshot cacheado
     */
    private fun addLocalStorageToZip(zipOut: ZipOutputStream, snapshot: String?) {
        val dataJson = snapshot ?: run {
            JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0")
                put("note", "Snapshot no disponible - se usó placeholder")
            }.toString(2)
        }

        val entry = ZipEntry("data.json")
        zipOut.putNextEntry(entry)
        zipOut.write(dataJson.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
    }

    /**
     * Añade un CSV equivalente a data.json al ZIP (si hay snapshot disponible)
     */
    private fun addCsvExportToZip(zipOut: ZipOutputStream, snapshot: String?) {
        if (snapshot.isNullOrBlank()) {
            Log.w(TAG, "Snapshot no disponible, omitiendo data.csv en el backup")
            return
        }

        val csvContent = try {
            createCsvFromSnapshot(snapshot)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo generar CSV desde snapshot", e)
            null
        } ?: return

        val entry = ZipEntry("data.csv")
        zipOut.putNextEntry(entry)
        zipOut.write(csvContent.toByteArray(Charsets.UTF_8))
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
     * Añade todas las fotos asociadas a registros al ZIP
     */
    private fun addPhotoFilesToZip(zipOut: ZipOutputStream) {
        if (!photoDir.exists()) {
            Log.d(TAG, "No hay carpeta de fotos")
            return
        }

        val photoFiles = photoDir.listFiles()?.filter { it.isFile } ?: emptyList()
        Log.d(TAG, "Añadiendo ${photoFiles.size} fotos al backup")

        photoFiles.forEach { photoFile ->
            try {
                val entry = ZipEntry("photos/${photoFile.name}")
                zipOut.putNextEntry(entry)

                FileInputStream(photoFile).use { input ->
                    input.copyTo(zipOut)
                }

                zipOut.closeEntry()
            } catch (e: Exception) {
                Log.e(TAG, "Error al añadir foto ${photoFile.name}", e)
            }
        }
    }

    /**
     * Resultado de restaurar un backup
     */
    data class RestoreResult(
        val success: Boolean,
        val dataJson: String? = null,
        val restoredAudios: Int = 0,
        val restoredPhotos: Int = 0,
        val message: String? = null
    )

    /**
     * Restaurar un backup ZIP seleccionado por el usuario
     *
     * @param uri Uri del archivo ZIP seleccionado (usualmente vía SAF)
     * @return RestoreResult con la información necesaria para sincronizar la UI
     */
    fun restoreBackup(uri: Uri): RestoreResult {
        val resolver = context.contentResolver
        val tempRoot = File(context.cacheDir, "restore_tmp").apply {
            if (exists()) {
                deleteRecursively()
            }
            mkdirs()
        }
        val tempAudioDir = File(tempRoot, "audio_notes").apply { mkdirs() }
        val tempPhotoDir = File(tempRoot, "entry_photos").apply { mkdirs() }

        return try {
            var dataJson: String? = null
            var restoredAudios = 0
            var restoredPhotos = 0

            resolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                    while (entry != null) {
                        val entryName = entry.name ?: ""

                        when {
                            entry.isDirectory -> {
                                // No hacemos nada con directorios vacíos
                            }
                            entryName.equals("data.json", ignoreCase = true) -> {
                                dataJson = zipIn.readBytes().toString(Charsets.UTF_8)
                            }
                            entryName.startsWith("audios/") -> {
                                val fileName = sanitizeEntryName(entryName.substringAfter('/'))
                                if (!fileName.isNullOrBlank()) {
                                    val targetFile = File(tempAudioDir, fileName)
                                    writeZipEntryToFile(zipIn, targetFile, buffer)
                                    restoredAudios++
                                }
                            }
                            entryName.startsWith("photos/") -> {
                                val fileName = sanitizeEntryName(entryName.substringAfter('/'))
                                if (!fileName.isNullOrBlank()) {
                                    val targetFile = File(tempPhotoDir, fileName)
                                    writeZipEntryToFile(zipIn, targetFile, buffer)
                                    restoredPhotos++
                                }
                            }
                            else -> {
                                Log.d(TAG, "Entrada desconocida en backup: $entryName")
                            }
                        }

                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            } ?: return RestoreResult(
                success = false,
                message = "No se pudo abrir el archivo seleccionado"
            )

            if (dataJson.isNullOrBlank()) {
                return RestoreResult(success = false, message = "El backup no contiene data.json")
            }

            // Reemplazar contenido actual con lo extraído en temporal
            replaceDirectoryContents(audioNotesDir, tempAudioDir)
            replaceDirectoryContents(photoDir, tempPhotoDir)

            // Actualizar snapshot para futuros autobackups
            saveLocalStorageSnapshot(dataJson!!)

            RestoreResult(
                success = true,
                dataJson = dataJson,
                restoredAudios = restoredAudios,
                restoredPhotos = restoredPhotos,
                message = "Backup restaurado correctamente"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al restaurar backup", e)
            RestoreResult(success = false, message = e.message ?: "Error al restaurar backup")
        } finally {
            tempRoot.deleteRecursively()
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
                addLocalStorageToZip(zipOut, localStorageData)

                // 2. Añadir data.csv con la exportación tabular
                addCsvExportToZip(zipOut, localStorageData)

                // 3. Añadir audios
                addAudioFilesToZip(zipOut)

                // 4. Añadir fotos
                addPhotoFilesToZip(zipOut)
            }

            // Guardar snapshot para futuros autobackups
            saveLocalStorageSnapshot(localStorageData)

            cleanOldBackups()

            updateLastBackupMetadata(backupFile, type = "manual")

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
     * Exportar todas las fotos en un ZIP
     */
    fun exportPhotosZip(): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val zipFile = File(context.cacheDir, "photos_$timestamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                val photoFiles = photoDir.listFiles()?.filter { it.isFile } ?: emptyList()
                photoFiles.forEach { photo ->
                    try {
                        val entry = ZipEntry("photos/${photo.name}")
                        zipOut.putNextEntry(entry)
                        FileInputStream(photo).use { input -> input.copyTo(zipOut) }
                        zipOut.closeEntry()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al añadir foto ${photo.name} al ZIP", e)
                    }
                }
            }

            zipFile
        } catch (e: Exception) {
            Log.e(TAG, "Error al exportar fotos", e)
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
     * Guardar snapshot del localStorage para usar en backups automáticos
     */
    fun saveLocalStorageSnapshot(snapshot: String) {
        try {
            localStorageSnapshotFile.writeText(snapshot)
            Log.d(TAG, "Snapshot de localStorage actualizado (${snapshot.length} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar snapshot de localStorage", e)
        }
    }

    /**
     * Leer snapshot más reciente del localStorage
     */
    private fun readLocalStorageSnapshot(): String? {
        return try {
            if (localStorageSnapshotFile.exists()) {
                localStorageSnapshotFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al leer snapshot de localStorage", e)
            null
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

    private fun writeZipEntryToFile(zipIn: ZipInputStream, targetFile: File, buffer: ByteArray) {
        targetFile.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

        FileOutputStream(targetFile).use { output ->
            BufferedOutputStream(output).use { buffered ->
                var bytesRead = zipIn.read(buffer)
                while (bytesRead != -1) {
                    buffered.write(buffer, 0, bytesRead)
                    bytesRead = zipIn.read(buffer)
                }
                buffered.flush()
            }
        }
    }

    private fun replaceDirectoryContents(targetDir: File, sourceDir: File) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        } else {
            targetDir.listFiles()?.forEach { file ->
                if (!file.deleteRecursively()) {
                    Log.w(TAG, "No se pudo eliminar ${file.name} antes de restaurar backup")
                }
            }
        }

        sourceDir.listFiles()?.forEach { sourceFile ->
            val destination = File(targetDir, sourceFile.name)
            try {
                sourceFile.copyTo(destination, overwrite = true)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo copiar ${sourceFile.name} al directorio definitivo", e)
            }
        }
    }

    private fun sanitizeEntryName(rawName: String): String? {
        val candidate = rawName.substringAfterLast('/').trim()
        if (candidate.isEmpty() || candidate.contains("..")) {
            return null
        }
        return candidate
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

    /**
     * Persistir información del último backup creado (auto o manual)
     */
    private fun updateLastBackupMetadata(file: File, type: String) {
        try {
            val timestamp = if (file.exists()) file.lastModified() else System.currentTimeMillis()
            backupPrefs.edit()
                .putLong("last_backup_timestamp", timestamp)
                .putString("last_backup_path", file.absolutePath)
                .putString("last_backup_filename", file.name)
                .putString("last_backup_type", type)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando metadata de backup", e)
        }
    }

    fun getLastBackupMetadata(): LastBackupMetadata? {
        val timestamp = backupPrefs.getLong("last_backup_timestamp", -1L)
        if (timestamp <= 0) return null

        val path = backupPrefs.getString("last_backup_path", null) ?: return null
        val filename = backupPrefs.getString("last_backup_filename", null) ?: return null
        val type = backupPrefs.getString("last_backup_type", "manual") ?: "manual"

        return LastBackupMetadata(
            absolutePath = path,
            filename = filename,
            timestamp = timestamp,
            type = type
        )
    }

    data class LastBackupMetadata(
        val absolutePath: String,
        val filename: String,
        val timestamp: Long,
        val type: String
    ) {
        val formattedDate: String
            get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    fun getBackupsDirectory(): File = backupsDir

    /**
     * Genera un CSV siguiendo el mismo formato que la exportación manual desde WebView
     */
    @Throws(JSONException::class)
    private fun createCsvFromSnapshot(snapshot: String): String {
        val root = JSONObject(snapshot)
        val substancesArray = root.optJSONArray("substances") ?: JSONArray()
        val entriesArray = root.optJSONArray("entries") ?: JSONArray()

        val csvBuilder = StringBuilder()
        csvBuilder.append('\uFEFF') // BOM para UTF-8

        // Sección de sustancias
        csvBuilder.append("SUSTANCIAS\n")
        csvBuilder.append("ID;Nombre;Color;Emoji;Fecha_Creacion;Fecha_Actualizacion\n")

        for (i in 0 until substancesArray.length()) {
            val obj = substancesArray.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            val color = obj.optString("color")
            val emoji = obj.optString("emoji", suggestEmojiFallback(name))
            val createdAt = obj.optString("createdAt")
            val updatedAt = obj.optString("updatedAt")

            val row = listOf(
                id,
                quoteForCsv(name),
                quoteForCsv(color),
                quoteForCsv(emoji),
                quoteForCsv(createdAt),
                quoteForCsv(updatedAt)
            ).joinToString(";")

            csvBuilder.append(row).append('\n')
        }

        csvBuilder.append('\n')

        // Sección de registros
        csvBuilder.append("REGISTROS\n")
        csvBuilder.append("ID;Sustancia;Dosis;Unidad;Fecha_Hora;Set;Setting;Notas;Fecha_Creacion;Fecha_Actualizacion\n")

        for (i in 0 until entriesArray.length()) {
            val obj = entriesArray.optJSONObject(i) ?: continue
            val row = listOf(
                obj.optString("id"),
                quoteForCsv(obj.optString("substance")),
                obj.optDouble("dose", 0.0).toString(),
                quoteForCsv(obj.optString("unit")),
                quoteForCsv(obj.optString("date")),
                quoteForCsv(obj.optString("set")),
                quoteForCsv(obj.optString("setting")),
                quoteForCsv(obj.optString("notes")),
                quoteForCsv(obj.optString("createdAt")),
                quoteForCsv(obj.optString("updatedAt"))
            ).joinToString(";")

            csvBuilder.append(row).append('\n')
        }

        return csvBuilder.toString()
    }

    private fun quoteForCsv(value: String?): String {
        val sanitized = value ?: ""
        return "\"${sanitized.replace("\"", "\"\"")}\""
    }

    /**
     * Fallback simple de emoji, replicando la lógica básica del front
     */
    private fun suggestEmojiFallback(name: String?): String {
        val lower = name?.lowercase(Locale.getDefault()) ?: return "💊"
        return when {
            lower.contains("lsd") || lower.contains("ácido") -> "🌈"
            lower.contains("ket") || lower.contains("ketamina") -> "❄️"
            lower.contains("opio") || lower.contains("hero") || lower.contains("morf") -> "🌿"
            lower.contains("mdma") || lower.contains("éxtasis") -> "💎"
            lower.contains("coca") -> "❄️"
            lower.contains("anfet") || lower.contains("speed") || lower.contains("meth") -> "⚡"
            lower.contains("cannabis") || lower.contains("marihuana") || lower.contains("hach") -> "🌿"
            lower.contains("psiloc") || lower.contains("hongo") -> "🍄"
            lower.contains("dmt") -> "👁️"
            lower.contains("mescal") || lower.contains("peyote") -> "🌵"
            lower.contains("alcohol") -> "🍷"
            lower.contains("nicotina") || lower.contains("tabaco") -> "🚬"
            lower.contains("cafe") || lower.contains("té") -> "☕"
            else -> "💊"
        }
    }
}
