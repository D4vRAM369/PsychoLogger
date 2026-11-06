package com.d4vram.psychologger

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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
import javax.crypto.CipherInputStream
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
        val message: String? = null,
        val needsPassword: Boolean = false
    )

    /**
     * Restaurar un backup ZIP seleccionado por el usuario
     *
     * @param uri Uri del archivo ZIP seleccionado (usualmente vía SAF)
     * @return RestoreResult con la información necesaria para sincronizar la UI
     */
    fun restoreBackup(uri: Uri, password: String? = null): RestoreResult {
        val resolver = context.contentResolver
        val tempRoot = File(context.cacheDir, "restore_tmp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        return try {
            // 1. Detectar si es un backup cifrado
            var isEncrypted = false
            var metadataEntry: String? = null

            resolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == "metadata.json") {
                            isEncrypted = true
                            metadataEntry = zipIn.readBytes().toString(Charsets.UTF_8)
                            break
                        }
                        entry = zipIn.nextEntry
                    }
                }
            }

            if (isEncrypted) {
                if (password == null) {
                    return RestoreResult(success = false, needsPassword = true, message = context.getString(R.string.n_bk_encrypted))
                }
                
                // Proceder con la des-cifración
                val result = restoreEncryptedBackup(uri, password, tempRoot, metadataEntry!!)
                if (!result.success) return result
                return result
            }

            // 2. Backup normal (legacy o directo)
            resolver.openInputStream(uri)?.use { inputStream ->
                processZipStream(ZipInputStream(BufferedInputStream(inputStream)), tempRoot)
            } ?: RestoreResult(success = false, message = context.getString(R.string.n_bk_open_fail))

        } catch (e: Exception) {
            Log.e(TAG, "Error al restaurar backup", e)
            RestoreResult(success = false, message = e.message ?: context.getString(R.string.n_bk_restore_err))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun restoreEncryptedBackup(uri: Uri, password: String, tempRoot: File, metadataJson: String): RestoreResult {
        return try {
            val metadata = JSONObject(metadataJson)
            val salt = hexToBytes(metadata.getString("salt"))
            val iv = hexToBytes(metadata.getString("iv"))
            
            val key = deriveKey(password, salt)
            val chunked = metadata.optString("format") == ChunkedCrypto.FORMAT

            val encryptedFile = File(context.cacheDir, "temp_data.enc")
            val decryptedZip = File(context.cacheDir, "temp_decrypted.zip")

            try {
                // Extraer data.enc del ZIP original
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            if (entry.name == "data.enc") {
                                FileOutputStream(encryptedFile).use { zipIn.copyTo(it) }
                                break
                            }
                            entry = zipIn.nextEntry
                        }
                    }
                }

                // Descifrar
                if (chunked) {
                    BufferedInputStream(FileInputStream(encryptedFile)).use { inp ->
                        BufferedOutputStream(FileOutputStream(decryptedZip)).use { out ->
                            ChunkedCrypto.decrypt(inp, out, key, iv)
                        }
                    }
                } else {
                    // Formato antiguo (1.x): un único GCM. Compatibilidad con backups previos.
                    val cipher = Cipher.getInstance(AES_MODE)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                    CipherInputStream(FileInputStream(encryptedFile), cipher).use { cipherIn ->
                        FileOutputStream(decryptedZip).use { cipherIn.copyTo(it) }
                    }
                }

                // Procesar el ZIP descifrado
                processZipStream(ZipInputStream(BufferedInputStream(FileInputStream(decryptedZip))), tempRoot)
            } finally {
                // Limpia archivos temp aunque haya excepciones — datos descifrados
                // no deben quedar en cacheDir si algo falla.
                encryptedFile.delete()
                decryptedZip.delete()
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Backup antiguo demasiado grande para descifrar en memoria", e)
            RestoreResult(success = false, message = context.getString(R.string.n_bk_too_big))
        } catch (e: Exception) {
            Log.e(TAG, "Error descifrando backup", e)
            RestoreResult(success = false, message = context.getString(R.string.n_bk_bad_pwd))
        }
    }

    private fun processZipStream(zipIn: ZipInputStream, tempRoot: File): RestoreResult {
        val tempAudioDir = File(tempRoot, "audio_notes").apply { mkdirs() }
        val tempPhotoDir = File(tempRoot, "entry_photos").apply { mkdirs() }
        var dataJson: String? = null
        var restoredAudios = 0
        var restoredPhotos = 0
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        zipIn.use { zi ->
            var entry = zi.nextEntry
            while (entry != null) {
                val entryName = entry.name ?: ""
                when {
                    entryName.equals("data.json", ignoreCase = true) -> {
                        dataJson = zi.readBytes().toString(Charsets.UTF_8)
                    }
                    entryName.startsWith("audios/") -> {
                        val fileName = sanitizeEntryName(entryName.substringAfter('/'))
                        if (!fileName.isNullOrBlank()) {
                            writeZipEntryToFile(zi, File(tempAudioDir, fileName), buffer)
                            restoredAudios++
                        }
                    }
                    entryName.startsWith("photos/") -> {
                        val fileName = sanitizeEntryName(entryName.substringAfter('/'))
                        if (!fileName.isNullOrBlank()) {
                            writeZipEntryToFile(zi, File(tempPhotoDir, fileName), buffer)
                            restoredPhotos++
                        }
                    }
                }
                zi.closeEntry()
                entry = zi.nextEntry
            }
        }

        if (dataJson.isNullOrBlank()) {
            return RestoreResult(success = false, message = context.getString(R.string.n_bk_no_data))
        }

        // Aplicar cambios
        val audioRestoreOk = replaceDirectoryContents(audioNotesDir, tempAudioDir)
        val photoRestoreOk = replaceDirectoryContents(photoDir, tempPhotoDir)

        if (!audioRestoreOk || !photoRestoreOk) {
            return RestoreResult(
                success = false,
                message = context.getString(R.string.n_bk_media_fail)
            )
        }

        saveLocalStorageSnapshot(dataJson!!)

        return RestoreResult(
            success = true,
            dataJson = dataJson,
            restoredAudios = restoredAudios,
            restoredPhotos = restoredPhotos,
            message = context.getString(R.string.n_bk_restored)
        )
    }

    private fun hexToBytes(hex: String): ByteArray {
        return ByteArray(hex.length / 2) {
            hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Crear backup con datos de localStorage (llamado desde JavaScript/UI)
     *
     * @param localStorageData JSON string con todo el localStorage
     * @param password Contraseña opcional para cifrar el backup completo
     * @param includeMedia Si se deben incluir audios y fotos
     * @return File del backup o null
     */
    fun createBackupWithData(
        localStorageData: String,
        password: String? = null,
        includeMedia: Boolean = true
    ): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val baseName = if (password != null) "backup_encrypted_$timestamp" else "backup_$timestamp"
            val backupFile = File(backupsDir, "$baseName.zip")

            // Si hay contraseña, creamos primero un ZIP temporal y luego lo ciframos
            val fileToReturn = if (password != null) {
                val tempZip = File(context.cacheDir, "temp_backup_$timestamp.zip")
                try {
                    createZip(tempZip, localStorageData, includeMedia)

                    val encryptedFile = File(backupsDir, "$baseName.zip")
                    encryptFile(tempZip, encryptedFile, password)

                    encryptedFile
                } finally {
                    if (!tempZip.delete() && tempZip.exists()) {
                        Log.w(TAG, "No se pudo eliminar ZIP temporal ${tempZip.name}")
                    }
                }
            } else {
                createZip(backupFile, localStorageData, includeMedia)
                backupFile
            }

            // Guardar snapshot para futuros autobackups
            saveLocalStorageSnapshot(localStorageData)
            cleanOldBackups()
            updateLastBackupMetadata(fileToReturn, type = "manual")

            fileToReturn

        } catch (e: Exception) {
            Log.e(TAG, "Error al crear backup con datos", e)
            null
        }
    }

    /**
     * Helper para crear un ZIP con datos y multimedia
     */
    private fun createZip(outputFile: File, localStorageData: String, includeMedia: Boolean) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
            // 1. Añadir data.json con los datos reales
            addLocalStorageToZip(zipOut, localStorageData)

            // 2. Añadir data.csv con la exportación tabular
            addCsvExportToZip(zipOut, localStorageData)

            if (includeMedia) {
                // 3. Añadir audios
                addAudioFilesToZip(zipOut)

                // 4. Añadir fotos
                addPhotoFilesToZip(zipOut)
            }
        }
    }

    /**
     * Cifra un archivo cualquiera y genera un paquete ZIP compatible (metadata.json + data.enc)
     */
    private fun encryptFile(inputFile: File, outputFile: File, password: String) {
        // 1. Generar salt e IV
        val salt = generateRandomBytes(SALT_LENGTH)
        val iv = generateRandomBytes(IV_LENGTH)

        // 2. Derivar clave de la contraseña
        val key = deriveKey(password, salt)

        // 3. Cifrar el archivo de entrada
        val encryptedData = File(context.cacheDir, "data.enc.tmp")
        try {
            // Streaming por bloques: un GCM único acumula todo en heap (OOM con backups grandes)
            BufferedOutputStream(FileOutputStream(encryptedData)).use { out ->
                FileInputStream(inputFile).use { ChunkedCrypto.encrypt(it, out, key, iv) }
            }

            // 4. Crear ZIP final con metadata + datos cifrados
            ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
                // Metadata
                val metadata = JSONObject().apply {
                    put("algorithm", "AES-256-GCM")
                    put("salt", bytesToHex(salt))
                    put("iv", bytesToHex(iv))
                    put("iterations", PBKDF2_ITERATIONS)
                    put("timestamp", System.currentTimeMillis())
                    put("type", "comprehensive_backup")
                    put("format", ChunkedCrypto.FORMAT)
                    put("chunkSize", ChunkedCrypto.DEFAULT_CHUNK)
                }

                zipOut.putNextEntry(ZipEntry("metadata.json"))
                zipOut.write(metadata.toString(2).toByteArray())
                zipOut.closeEntry()

                // Datos cifrados
                zipOut.putNextEntry(ZipEntry("data.enc"))
                FileInputStream(encryptedData).use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        } finally {
            // Limpiar archivo temporal cifrado incluso si el empaquetado falla.
            if (!encryptedData.delete() && encryptedData.exists()) {
                Log.w(TAG, "No se pudo eliminar temporal cifrado ${encryptedData.name}")
            }
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
        val encryptedData = File(context.cacheDir, "data.enc")
        try {
            BufferedOutputStream(FileOutputStream(encryptedData)).use { out ->
                FileInputStream(tempZip).use { ChunkedCrypto.encrypt(it, out, key, iv) }
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
                    put("format", ChunkedCrypto.FORMAT)
                    put("chunkSize", ChunkedCrypto.DEFAULT_CHUNK)
                }

                zipOut.putNextEntry(ZipEntry("metadata.json"))
                zipOut.write(metadata.toString(2).toByteArray())
                zipOut.closeEntry()

                // Datos cifrados
                zipOut.putNextEntry(ZipEntry("data.enc"))
                FileInputStream(encryptedData).use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        } finally {
            // Limpiar archivos temporales aunque falle el cifrado o el ZIP final.
            if (!tempZip.delete() && tempZip.exists()) {
                Log.w(TAG, "No se pudo eliminar ZIP temporal ${tempZip.name}")
            }
            if (!encryptedData.delete() && encryptedData.exists()) {
                Log.w(TAG, "No se pudo eliminar temporal cifrado ${encryptedData.name}")
            }
        }
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
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secret = factory.generateSecret(spec)
            SecretKeySpec(secret.encoded, "AES")
        } finally {
            // Limpia el char[] interno con la password — evita que quede en heap
            // hasta el GC. (El String 'password' en sí es inmutable, no podemos
            // limpiarlo, pero al menos minimizamos la ventana de exposición.)
            spec.clearPassword()
        }
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

    // Reemplaza targetDir con sourceDir de forma SEMI-ATÓMICA:
    // 1. mueve el directorio actual a .bak (rename = atómico en mismo filesystem)
    // 2. copia sourceDir → targetDir
    // 3. si algo falla, hace rollback del .bak; si todo OK, lo borra
    //
    // Por qué importa: el código anterior borraba todo y luego copiaba.
    // Si la app crasheaba o el device se apagaba a mitad, el usuario perdía
    // sus fotos/audios sin haber recibido los nuevos.
    private fun replaceDirectoryContents(targetDir: File, sourceDir: File): Boolean {
        val parent = targetDir.parentFile ?: run {
            Log.e(TAG, "No se pudo determinar parent de ${targetDir.path}")
            return false
        }
        val backup = File(parent, "${targetDir.name}.bak_${System.currentTimeMillis()}")

        // Paso 1: mueve los datos actuales a .bak (no destructivo)
        val movedToBackup = targetDir.exists() && targetDir.renameTo(backup)
        if (!movedToBackup && targetDir.exists()) {
            // Fallback: rename falló (cross-filesystem o permisos). Borra in-place.
            Log.w(TAG, "renameTo falló, borrado en sitio (no rollback posible)")
            targetDir.listFiles()?.forEach {
                if (!it.deleteRecursively()) Log.w(TAG, "No se pudo eliminar ${it.name}")
            }
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Log.e(TAG, "No se pudo crear ${targetDir.path}")
            if (movedToBackup && !backup.renameTo(targetDir)) {
                Log.e(TAG, "Rollback falló — datos quedan en ${backup.path}")
            }
            return false
        }

        // Paso 2: copia los nuevos archivos
        var allOk = true
        sourceDir.listFiles()?.forEach { sourceFile ->
            val destination = File(targetDir, sourceFile.name)
            try {
                sourceFile.copyTo(destination, overwrite = true)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo copiar ${sourceFile.name} al directorio definitivo", e)
                allOk = false
            }
        }

        // Paso 3: commit (todo OK → borra backup) o rollback (algo falló → restaura backup)
        if (movedToBackup) {
            if (allOk) {
                backup.deleteRecursively()
            } else {
                Log.w(TAG, "Copia incompleta — rollback desde ${backup.name}")
                targetDir.deleteRecursively()
                if (!backup.renameTo(targetDir)) {
                    Log.e(TAG, "Rollback falló — datos quedan en ${backup.path}")
                }
            }
        }

        return allOk
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
        val archivedSubstancesArray = root.optJSONArray("archivedSubstances") ?: JSONArray()
        val entriesArray = root.optJSONArray("entries") ?: JSONArray()

        val csvBuilder = StringBuilder()
        csvBuilder.append('\uFEFF') // BOM para UTF-8

        // Sección de sustancias
        csvBuilder.append("SUSTANCIAS\n")
        csvBuilder.append("ID;Nombre;Color;Emoji;Archivada;Fecha_Archivado;Fecha_Creacion;Fecha_Actualizacion;SMILES\n")

        appendSubstancesToCsv(csvBuilder, substancesArray, isArchivedFallback = false)
        appendSubstancesToCsv(csvBuilder, archivedSubstancesArray, isArchivedFallback = true)

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

    private fun appendSubstancesToCsv(csvBuilder: StringBuilder, substancesArray: JSONArray, isArchivedFallback: Boolean) {
        for (i in 0 until substancesArray.length()) {
            val obj = substancesArray.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            val color = obj.optString("color")
            val emoji = obj.optString("emoji", suggestEmojiFallback(name))
            val isArchived = obj.optBoolean("isArchived", isArchivedFallback)
            val archivedAt = obj.optString("archivedAt")
            val createdAt = obj.optString("createdAt")
            val updatedAt = obj.optString("updatedAt")

            val row = listOf(
                id,
                quoteForCsv(name),
                quoteForCsv(color),
                quoteForCsv(emoji),
                isArchived.toString(),
                quoteForCsv(archivedAt),
                quoteForCsv(createdAt),
                quoteForCsv(updatedAt),
                quoteForCsv(obj.optString("smiles"))
            ).joinToString(";")

            csvBuilder.append(row).append('\n')
        }
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

    // =========================================================================
    // MÉTODOS SAF (Storage Access Framework)
    // =========================================================================

    /**
     * Crear backup en caché temporal (para luego mover via SAF)
     *
     * CONCEPTO: Creamos el ZIP en cacheDir primero porque:
     * 1. Es rápido (almacenamiento interno)
     * 2. Si el usuario cancela SAF, no perdemos el trabajo
     * 3. Podemos reintentar guardar en otra ubicación
     *
     * @param localStorageData JSON string con localStorage del WebView
     * @param password Contraseña para cifrar (null = sin cifrar)
     * @param includeMedia Si incluir audios y fotos
     * @return File del backup temporal o null si falla
     */
    fun createBackupInCache(
        localStorageData: String,
        password: String? = null,
        includeMedia: Boolean = true
    ): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val baseName = if (password != null) "backup_encrypted_$timestamp" else "backup_$timestamp"
            val backupFile = File(context.cacheDir, "$baseName.zip")

            if (password != null) {
                // Crear ZIP temporal sin cifrar
                val tempZip = File(context.cacheDir, "temp_backup_$timestamp.zip")
                try {
                    createZip(tempZip, localStorageData, includeMedia)
                    // Cifrar y guardar
                    encryptFile(tempZip, backupFile, password)
                } finally {
                    tempZip.delete() // no dejar 100+ MB sin cifrar en caché si falla
                }
            } else {
                createZip(backupFile, localStorageData, includeMedia)
            }

            // Guardar snapshot para futuros autobackups
            saveLocalStorageSnapshot(localStorageData)

            Log.d(TAG, "Backup creado en caché: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
            backupFile

        } catch (e: Exception) {
            Log.e(TAG, "Error al crear backup en caché", e)
            null
        }
    }

    /**
     * Escribir archivo de backup a un URI de SAF
     *
     * CONCEPTO: ContentResolver + OutputStream
     * SAF no trabaja con File paths tradicionales, sino con URIs.
     * Usamos ContentResolver para abrir un OutputStream hacia el destino.
     *
     * @param sourceFile Archivo origen (en cacheDir)
     * @param destinationUri URI destino obtenido de SAF picker
     * @return true si se copió correctamente
     */
    fun writeBackupToUri(sourceFile: File, destinationUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw Exception("No se pudo abrir OutputStream para URI: $destinationUri")

            Log.d(TAG, "Backup escrito a URI: $destinationUri")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo backup a URI", e)
            false
        }
    }

    /**
     * Crear archivo de backup en una carpeta SAF (para autobackups)
     *
     * @param folderUri URI de la carpeta obtenida de OpenDocumentTree
     * @param sourceFile Archivo fuente en cacheDir
     * @param fileName Nombre del archivo a crear
     * @return true si se creó y copió correctamente
     */
    fun createBackupInSafFolder(folderUri: Uri, sourceFile: File, fileName: String): Boolean {
        return try {
            val folder = DocumentFile.fromTreeUri(context, folderUri)
                ?: throw Exception("No se pudo acceder a la carpeta SAF")

            // Crear el archivo en la carpeta
            val newFile = folder.createFile("application/zip", fileName)
                ?: throw Exception("No se pudo crear archivo en carpeta SAF")

            // Copiar contenido
            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Backup creado en carpeta SAF: $fileName")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error creando backup en carpeta SAF", e)
            false
        }
    }

    /**
     * Listar backups en una carpeta SAF
     *
     * @param folderUri URI de la carpeta
     * @return Lista de DocumentFile ordenados por fecha (más antiguo primero)
     */
    fun listBackupsInSafFolder(folderUri: Uri): List<DocumentFile> {
        return try {
            val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()

            folder.listFiles()
                .filter { it.isFile && it.name?.endsWith(".zip") == true }
                .filter { it.name?.contains("backup") == true || it.name?.contains("autobackup") == true }
                .sortedBy { it.lastModified() }  // Más antiguo primero

        } catch (e: Exception) {
            Log.e(TAG, "Error listando backups en SAF", e)
            emptyList()
        }
    }

    /**
     * Limpiar backups antiguos en carpeta SAF (rotación)
     *
     * CONCEPTO: Mantener solo los últimos N backups para no llenar storage.
     *
     * @param folderUri URI de la carpeta
     * @param keepCount Cantidad de backups a mantener (default 7)
     */
    fun cleanOldBackupsInSaf(folderUri: Uri, keepCount: Int = MAX_BACKUPS) {
        try {
            val backups = listBackupsInSafFolder(folderUri)
            val toDelete = backups.dropLast(keepCount)  // Mantener los últimos keepCount

            toDelete.forEach { file ->
                val deleted = file.delete()
                Log.d(TAG, "Eliminando backup antiguo SAF: ${file.name} -> ${if (deleted) "OK" else "FAIL"}")
            }

            if (toDelete.isNotEmpty()) {
                Log.d(TAG, "Rotación SAF: eliminados ${toDelete.size} backups antiguos")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error en rotación de backups SAF", e)
        }
    }

    /**
     * Eliminar archivo temporal de caché
     */
    fun deleteTempBackup(file: File): Boolean {
        return try {
            val deleted = file.delete()
            Log.d(TAG, "Backup temporal eliminado: ${file.name} -> ${if (deleted) "OK" else "FAIL"}")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando backup temporal", e)
            false
        }
    }

    /**
     * Generar nombre de archivo para autobackup
     */
    fun generateAutobackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        return "autobackup_$timestamp.zip"
    }
}
