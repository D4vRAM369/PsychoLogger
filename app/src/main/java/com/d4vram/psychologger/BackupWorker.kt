package com.d4vram.psychologger

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BackupWorker - Worker de WorkManager para backups automáticos periódicos
 *
 * CONCEPTO: WorkManager Worker
 * - Se ejecuta en background incluso si la app está cerrada
 * - Sobrevive a reinicios del dispositivo
 * - Respeta el modo de ahorro de batería (Doze)
 * - Garantiza ejecución eventual (puede retrasarse)
 *
 * FLUJO SAF:
 * 1. WorkManager programa este Worker cada 12 horas
 * 2. Verificamos si autobackup está habilitado y hay URI configurado
 * 3. Verificamos que aún tenemos permisos SAF
 * 4. Creamos backup en caché y lo copiamos a carpeta SAF
 * 5. Aplicamos rotación (mantener últimos 7)
 * 6. Mostramos Toast si la app está visible
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "BackupWorker"
        const val WORK_NAME = "periodic_backup"
    }

    private val backupPrefs by lazy { BackupPreferences(applicationContext) }
    private val backupManager by lazy { BackupManager(applicationContext) }

    /**
     * Método principal que ejecuta el backup automático via SAF
     *
     * @return Result.success() si todo OK, Result.failure() si falla permanentemente,
     *         Result.retry() si debe reintentar
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "BackupWorker iniciado")

        // 1. Verificar si autobackup está habilitado
        if (!backupPrefs.isAutobackupEnabled()) {
            Log.d(TAG, "Autobackup deshabilitado, saltando...")
            return@withContext Result.success()
        }

        // 2. Obtener URI de carpeta configurada
        val folderUri = backupPrefs.getAutobackupFolderUri()
        if (folderUri == null) {
            Log.w(TAG, "No hay carpeta de autobackup configurada")
            showToastOnMainThread("❌ Autobackup: No hay carpeta configurada")
            return@withContext Result.failure()
        }

        // 3. Verificar que aún tenemos permisos SAF
        if (!backupPrefs.hasValidPermissions()) {
            Log.e(TAG, "Permisos SAF revocados o inválidos")
            showToastOnMainThread("❌ Autobackup: Permisos revocados. Reconfigura la carpeta.")
            // Desactivar autobackup para evitar fallos repetidos
            backupPrefs.setAutobackupEnabled(false)
            return@withContext Result.failure()
        }

        return@withContext try {
            showToastOnMainThread("🔄 Iniciando backup automático...")

            // 4. Leer snapshot de localStorage (guardado por la app)
            val localStorageData = readLocalStorageSnapshot()
            if (localStorageData.isNullOrBlank()) {
                Log.w(TAG, "No hay snapshot de localStorage disponible")
                showToastOnMainThread("❌ Autobackup: No hay datos para respaldar")
                return@withContext Result.failure()
            }

            // 5. Crear backup en caché (sin cifrar para autobackups)
            val tempFile = backupManager.createBackupInCache(
                localStorageData = localStorageData,
                password = null,  // Autobackups sin cifrar
                includeMedia = true
            )

            if (tempFile == null) {
                Log.e(TAG, "No se pudo crear backup temporal")
                showToastOnMainThread("❌ Autobackup: Error al crear backup")
                return@withContext Result.retry()
            }

            showToastOnMainThread("💾 Guardando backup automático...")

            // 6. Crear archivo en carpeta SAF
            val fileName = backupManager.generateAutobackupFileName()
            val success = backupManager.createBackupInSafFolder(folderUri, tempFile, fileName)

            // 7. Limpiar archivo temporal
            backupManager.deleteTempBackup(tempFile)

            if (!success) {
                Log.e(TAG, "No se pudo escribir a carpeta SAF")
                showToastOnMainThread("❌ Autobackup: Error al guardar en destino")
                return@withContext Result.retry()
            }

            // 8. Rotación: mantener últimos 7 backups
            backupManager.cleanOldBackupsInSaf(folderUri, keepCount = 7)

            // 9. Actualizar timestamp
            backupPrefs.updateLastAutobackupTime()

            Log.d(TAG, "Autobackup completado exitosamente: $fileName")
            showToastOnMainThread("✅ Backup automático completado")

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error en autobackup", e)
            showToastOnMainThread("❌ Autobackup falló: ${e.message}")
            Result.retry()
        }
    }

    /**
     * Leer snapshot de localStorage guardado por la app
     */
    private fun readLocalStorageSnapshot(): String? {
        return try {
            val snapshotFile = java.io.File(applicationContext.filesDir, "backup_cache/local_storage_snapshot.json")
            if (snapshotFile.exists()) {
                snapshotFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo snapshot de localStorage", e)
            null
        }
    }

    /**
     * Mostrar Toast en el hilo principal (solo si la app está en primer plano)
     *
     * CONCEPTO: Toasts desde Workers
     * - Los Workers corren en threads de background
     * - Los Toasts deben mostrarse en el Main thread
     * - Solo mostramos si la app está visible (mejor UX)
     */
    private fun showToastOnMainThread(message: String) {
        if (!isAppInForeground()) {
            Log.d(TAG, "App en background, Toast omitido: $message")
            return
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Verificar si la app está en primer plano
     *
     * CONCEPTO: ActivityManager.RunningAppProcessInfo
     * - IMPORTANCE_FOREGROUND = app visible en pantalla
     * - Evitamos mostrar Toasts si el usuario no está mirando la app
     */
    private fun isAppInForeground(): Boolean {
        val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = applicationContext.packageName
        return appProcesses.any { processInfo ->
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
            processInfo.processName == packageName
        }
    }
}
