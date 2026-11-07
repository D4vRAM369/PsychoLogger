package com.d4vram.psychologger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * BackupPreferences - Gestión de configuración de backups SAF
 *
 * CONCEPTOS CLAVE:
 * - URI Persistente: Permiso que sobrevive reinicios de app/dispositivo
 * - takePersistableUriPermission: Guarda el permiso permanentemente
 * - persistedUriPermissions: Lista de URIs con permisos activos
 *
 * FLUJO:
 * 1. Usuario elige carpeta via SAF (OpenDocumentTree)
 * 2. App llama takePersistableUriPermission()
 * 3. URI se guarda aquí en SharedPreferences
 * 4. BackupWorker usa este URI para autobackups
 */
class BackupPreferences(private val context: Context) {

    companion object {
        private const val TAG = "BackupPreferences"
        private const val PREFS_NAME = "backup_saf_prefs"

        private const val KEY_AUTOBACKUP_URI = "autobackup_folder_uri"
        private const val KEY_AUTOBACKUP_ENABLED = "autobackup_enabled"
        private const val KEY_LAST_AUTOBACKUP = "last_autobackup_timestamp"
        private const val KEY_AUTOBACKUP_FOLDER_NAME = "autobackup_folder_name"
        private const val KEY_AUTOBACKUP_INTERVAL_HOURS = "autobackup_interval_hours"
        private const val KEY_AUTOBACKUP_KEEP_COUNT = "autobackup_keep_count"

        const val DEFAULT_INTERVAL_HOURS = 12
        const val DEFAULT_KEEP_COUNT = 7

        val INTERVAL_OPTIONS_HOURS = listOf(6, 12, 24, 48, 168)
        val KEEP_COUNT_OPTIONS = listOf(3, 5, 7, 10, 15)

        fun normalizeIntervalHours(hours: Int): Int =
            if (hours in INTERVAL_OPTIONS_HOURS) hours else DEFAULT_INTERVAL_HOURS

        fun normalizeKeepCount(count: Int): Int =
            if (count in KEEP_COUNT_OPTIONS) count else DEFAULT_KEEP_COUNT
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Guardar URI de carpeta de autobackups
     *
     * IMPORTANTE: Antes de llamar esto, debes haber llamado
     * contentResolver.takePersistableUriPermission(uri, flags)
     *
     * @param uri URI de la carpeta seleccionada via SAF
     * @param folderName Nombre legible de la carpeta para mostrar en UI
     */
    fun saveAutobackupFolderUri(uri: Uri?, folderName: String? = null) {
        prefs.edit().apply {
            if (uri != null) {
                putString(KEY_AUTOBACKUP_URI, uri.toString())
                putString(KEY_AUTOBACKUP_FOLDER_NAME, folderName ?: "Carpeta seleccionada")
                Log.d(TAG, "URI de autobackup guardado: $uri")
            } else {
                remove(KEY_AUTOBACKUP_URI)
                remove(KEY_AUTOBACKUP_FOLDER_NAME)
                Log.d(TAG, "URI de autobackup eliminado")
            }
            apply()
        }
    }

    /**
     * Obtener URI de carpeta de autobackups
     *
     * @return Uri guardado o null si no está configurado
     */
    fun getAutobackupFolderUri(): Uri? {
        val uriString = prefs.getString(KEY_AUTOBACKUP_URI, null)
        return uriString?.let {
            try {
                Uri.parse(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing URI: $it", e)
                null
            }
        }
    }

    /**
     * Obtener nombre legible de la carpeta para UI
     */
    fun getAutobackupFolderName(): String? {
        return prefs.getString(KEY_AUTOBACKUP_FOLDER_NAME, null)
    }

    /**
     * Habilitar/deshabilitar autobackup
     */
    fun setAutobackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOBACKUP_ENABLED, enabled).apply()
        Log.d(TAG, "Autobackup ${if (enabled) "habilitado" else "deshabilitado"}")
    }

    /**
     * Verificar si autobackup está habilitado
     */
    fun isAutobackupEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTOBACKUP_ENABLED, false)
    }

    /**
     * Obtener intervalo de autobackup en horas (6, 12, 24, 48, 168)
     */
    fun getAutobackupIntervalHours(): Int {
        val raw = prefs.getInt(KEY_AUTOBACKUP_INTERVAL_HOURS, DEFAULT_INTERVAL_HOURS)
        return normalizeIntervalHours(raw)
    }

    /**
     * Guardar intervalo de autobackup en horas
     */
    fun setAutobackupIntervalHours(hours: Int) {
        val valid = normalizeIntervalHours(hours)
        prefs.edit().putInt(KEY_AUTOBACKUP_INTERVAL_HOURS, valid).apply()
        Log.d(TAG, "Intervalo de autobackup actualizado: ${valid}h")
    }

    /**
     * Obtener cantidad de autobackups recientes a conservar (3, 5, 7, 10, 15)
     */
    fun getAutobackupKeepCount(): Int {
        val raw = prefs.getInt(KEY_AUTOBACKUP_KEEP_COUNT, DEFAULT_KEEP_COUNT)
        return normalizeKeepCount(raw)
    }

    /**
     * Guardar cantidad de autobackups recientes a conservar
     */
    fun setAutobackupKeepCount(count: Int) {
        val valid = normalizeKeepCount(count)
        prefs.edit().putInt(KEY_AUTOBACKUP_KEEP_COUNT, valid).apply()
        Log.d(TAG, "Cantidad de autobackups a conservar actualizada: $valid")
    }

    /**
     * Actualizar timestamp del último autobackup exitoso
     */
    fun updateLastAutobackupTime() {
        prefs.edit().putLong(KEY_LAST_AUTOBACKUP, System.currentTimeMillis()).apply()
    }

    /**
     * Obtener timestamp del último autobackup
     *
     * @return Timestamp en millis o -1 si nunca se ha hecho
     */
    fun getLastAutobackupTime(): Long {
        return prefs.getLong(KEY_LAST_AUTOBACKUP, -1L)
    }

    /**
     * Verificar si aún tenemos permisos para el URI guardado
     *
     * CONCEPTO: Los permisos SAF pueden ser revocados por el usuario
     * desde Ajustes del sistema, o si la carpeta es eliminada.
     *
     * @return true si tenemos permisos de lectura Y escritura
     */
    fun hasValidPermissions(): Boolean {
        val uri = getAutobackupFolderUri() ?: return false

        return context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri &&
            permission.isReadPermission &&
            permission.isWritePermission
        }
    }

    /**
     * Persistir permisos SAF para un URI
     *
     * @param uri URI obtenido del SAF picker
     * @return true si se persistió correctamente
     */
    fun persistUriPermissions(uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            context.contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "Permisos persistidos para: $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error persistiendo permisos", e)
            false
        }
    }

    /**
     * Liberar permisos SAF (al desconfigurar autobackup)
     */
    fun releaseUriPermissions() {
        val uri = getAutobackupFolderUri() ?: return

        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            context.contentResolver.releasePersistableUriPermission(uri, flags)
            Log.d(TAG, "Permisos liberados para: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando permisos", e)
        }
    }

    /**
     * Limpiar toda la configuración de autobackup
     */
    fun clearAutobackupConfig() {
        releaseUriPermissions()
        prefs.edit().apply {
            remove(KEY_AUTOBACKUP_URI)
            remove(KEY_AUTOBACKUP_ENABLED)
            remove(KEY_AUTOBACKUP_FOLDER_NAME)
            // Mantener KEY_LAST_AUTOBACKUP como historial
            apply()
        }
        Log.d(TAG, "Configuración de autobackup limpiada")
    }
}
