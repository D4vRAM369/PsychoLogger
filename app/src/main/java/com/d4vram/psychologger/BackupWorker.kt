package com.d4vram.psychologger

import android.content.Context
import android.util.Log
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
 * FLUJO:
 * 1. WorkManager programa este Worker cada 12 horas
 * 2. Android ejecuta doWork() cuando las condiciones se cumplen
 * 3. Creamos el backup usando BackupManager
 * 4. Retornamos Result.success() o Result.failure()
 *
 * VENTAJAS vs AlarmManager:
 * - Más eficiente energéticamente
 * - Más confiable (reintentos automáticos)
 * - Respeta las restricciones del sistema
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "BackupWorker"
        const val WORK_NAME = "periodic_backup"
    }

    /**
     * Método principal que ejecuta el backup
     *
     * CONCEPTO: Coroutines en Workers
     * - CoroutineWorker permite usar suspend functions
     * - doWork() se ejecuta en background automáticamente
     * - Podemos cambiar de dispatcher con withContext()
     *
     * @return Result.success() si todo OK, Result.failure() si falla
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Iniciando backup automático...")

            // Crear el backup
            val backupManager = BackupManager(applicationContext)
            val backupFile = backupManager.createBackup()

            if (backupFile != null) {
                Log.d(TAG, "Backup automático completado: ${backupFile.name}")
                Result.success()
            } else {
                Log.e(TAG, "Backup automático falló")
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error en backup automático", e)
            // Result.retry() reintentaría el trabajo según la política de reintentos
            Result.failure()
        }
    }
}
