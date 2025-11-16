package com.d4vram.psychologger

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * AudioPlayer - Clase helper para reproducir archivos de audio
 *
 * CONCEPTOS CLAVE:
 * - MediaPlayer maneja múltiples formatos (MP3, M4A, WAV, etc.)
 * - Tiene estados más simples que MediaRecorder
 * - Usa listeners para eventos asíncronos (onComplete, onError)
 * - Necesitamos un Handler para actualizar el progreso en tiempo real
 *
 * FLUJO TÍPICO:
 * setDataSource() -> prepare() -> start() -> [playing] -> completion
 *                                   ↓
 *                                pause() -> start() (resume)
 */
class AudioPlayer {

    // ESTADO INTERNO
    private var mediaPlayer: MediaPlayer? = null
    private var progressUpdateHandler: Handler? = null
    private var progressUpdateRunnable: Runnable? = null

    // Callback para comunicar eventos a quien use esta clase
    private var onProgressUpdate: ((progress: Float, currentMs: Int, totalMs: Int) -> Unit)? = null
    private var onCompletion: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    /**
     * Configura el callback que se ejecutará periódicamente durante la reproducción
     *
     * CONCEPTO: Lambdas en Kotlin
     * Una lambda es una función anónima que puedes pasar como parámetro.
     *
     * @param callback Función que recibe:
     *   - progress: Float entre 0.0 (inicio) y 1.0 (final)
     *   - currentMs: Posición actual en milisegundos
     *   - totalMs: Duración total en milisegundos
     *
     * EJEMPLO DE USO:
     * audioPlayer.setProgressCallback { progress, current, total ->
     *     println("Progreso: ${(progress * 100).toInt()}%")
     * }
     */
    fun setProgressCallback(callback: (Float, Int, Int) -> Unit) {
        onProgressUpdate = callback
    }

    /**
     * Configura el callback que se ejecutará cuando termine la reproducción
     */
    fun setCompletionCallback(callback: () -> Unit) {
        onCompletion = callback
    }

    /**
     * Configura el callback que se ejecutará si hay un error
     */
    fun setErrorCallback(callback: (String) -> Unit) {
        onError = callback
    }

    /**
     * Inicia la reproducción de un archivo de audio
     *
     * @param audioFile Archivo a reproducir (debe existir)
     * @return true si se inició correctamente, false si hubo error
     *
     * CONCEPTO: Try-catch para robustez
     * MediaPlayer puede fallar por muchas razones (archivo corrupto, formato no soportado, etc.)
     * Capturamos excepciones para no crashear la app.
     */
    fun play(audioFile: File): Boolean {
        if (!audioFile.exists()) {
            onError?.invoke("El archivo de audio no existe")
            return false
        }

        // Detener reproducción anterior si existe
        stop()

        return try {
            // Crear nuevo MediaPlayer
            val player = MediaPlayer().apply {
                // 1. Establecer fuente de datos
                setDataSource(audioFile.absolutePath)

                // 2. Preparar (carga metadata, buffering inicial)
                // prepare() es síncrono (bloquea el thread)
                // prepareAsync() sería asíncrono pero más complejo
                prepare()

                // 3. Listener de completado
                setOnCompletionListener {
                    // Este listener se ejecuta cuando el audio termina
                    stopProgressUpdates()
                    onCompletion?.invoke()
                }

                // 4. Listener de errores
                setOnErrorListener { _, what, extra ->
                    // what y extra son códigos de error de MediaPlayer
                    onError?.invoke("Error de reproducción: what=$what, extra=$extra")
                    stop()
                    true // true = "manejamos el error"
                }

                // 5. Iniciar reproducción
                start()
            }

            // Guardar referencia
            mediaPlayer = player

            // Iniciar actualizaciones de progreso
            startProgressUpdates()

            true
        } catch (e: Exception) {
            onError?.invoke("Error al reproducir: ${e.message}")
            false
        }
    }

    /**
     * Pausa la reproducción actual
     *
     * NOTA: MediaPlayer mantiene la posición, puedes resumir con resume()
     */
    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                stopProgressUpdates()
            }
        }
    }

    /**
     * Reanuda la reproducción después de pause()
     *
     * DIFERENCIA CON play():
     * - play() carga un archivo nuevo desde el inicio
     * - resume() continúa desde donde se pausó
     */
    fun resume() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                startProgressUpdates()
            }
        }
    }

    /**
     * Detiene completamente la reproducción y libera recursos
     *
     * CONCEPTO: Cleanup de recursos
     * MediaPlayer usa recursos nativos (decoders de hardware).
     * SIEMPRE debes llamar release() para liberarlos.
     */
    fun stop() {
        stopProgressUpdates()

        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            } catch (e: Exception) {
                // Ignorar errores al hacer cleanup
            }
        }

        mediaPlayer = null
    }

    /**
     * Verifica si hay audio reproduciéndose actualmente
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    /**
     * Obtiene la posición actual de reproducción
     *
     * @return Posición en milisegundos, o 0 si no hay reproducción
     */
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    /**
     * Obtiene la duración total del audio
     *
     * @return Duración en milisegundos, o 0 si no hay audio cargado
     */
    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    /**
     * Salta a una posición específica del audio
     *
     * @param milliseconds Posición en milisegundos
     *
     * ÚTIL PARA: Implementar seekbar (barra deslizable) en la UI
     */
    fun seekTo(milliseconds: Int) {
        mediaPlayer?.seekTo(milliseconds)
    }

    /**
     * Inicia el sistema de actualizaciones periódicas de progreso
     *
     * CONCEPTO: Handler + Runnable para tareas periódicas
     *
     * - Handler: Gestiona mensajes/tareas en un thread específico
     * - Looper.getMainLooper(): El thread principal (UI thread)
     * - Runnable: Interfaz con un método run() que se ejecuta
     * - postDelayed(): Programa una tarea para ejecutarse después de X ms
     *
     * Aquí usamos un patrón recursivo: el Runnable se auto-programa cada 100ms
     */
    private fun startProgressUpdates() {
        // Handler en el main thread (necesario para actualizar UI después)
        progressUpdateHandler = Handler(Looper.getMainLooper())

        // Crear el Runnable que se ejecutará cada 100ms
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        // Obtener progreso actual
                        val current = player.currentPosition
                        val total = player.duration
                        val progress = if (total > 0) {
                            current.toFloat() / total.toFloat()
                        } else {
                            0f
                        }

                        // Notificar al callback
                        onProgressUpdate?.invoke(progress, current, total)

                        // RECURSIÓN: Programar la próxima ejecución en 100ms
                        progressUpdateHandler?.postDelayed(this, 100)
                    }
                }
            }
        }

        // Iniciar el primer ciclo
        progressUpdateRunnable?.let { runnable ->
            progressUpdateHandler?.post(runnable)
        }
    }

    /**
     * Detiene las actualizaciones periódicas de progreso
     *
     * IMPORTANTE: Siempre detener el Handler para evitar memory leaks
     * Si no lo haces, el Runnable sigue ejecutándose incluso cuando ya no lo necesitas.
     */
    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let { runnable ->
            progressUpdateHandler?.removeCallbacks(runnable)
        }
        progressUpdateHandler = null
        progressUpdateRunnable = null
    }

    /**
     * Libera todos los recursos del reproductor
     *
     * LLAMAR ESTO EN:
     * - onDestroy() de Activity
     * - Cuando ya no necesites el reproductor
     *
     * Si no lo haces: memory leak (el MediaPlayer queda en memoria)
     */
    fun release() {
        stop()
        onProgressUpdate = null
        onCompletion = null
        onError = null
    }
}
