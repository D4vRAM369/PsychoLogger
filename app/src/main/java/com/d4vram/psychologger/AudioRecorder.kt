package com.d4vram.psychologger

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * AudioRecorder - Clase helper para simplificar la grabación de audio
 *
 * CONCEPTOS CLAVE:
 * - MediaRecorder tiene un ciclo de vida estricto que debemos respetar
 * - Los métodos deben llamarse en un orden específico o la app crashea
 * - Gestiona automáticamente la creación de archivos en filesDir
 *
 * FLUJO DE ESTADOS:
 * [Idle] -> setAudioSource() -> [Initialized] -> setOutputFormat() ->
 * setAudioEncoder() -> [Configured] -> prepare() -> [Prepared] ->
 * start() -> [Recording] -> stop() -> [Idle] -> release() -> [Released]
 */
class AudioRecorder(private val context: Context) {

    // ESTADO INTERNO
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimestamp: Long = 0L

    /**
     * Directorio donde se guardan los audios de forma persistente
     *
     * CONCEPTO: filesDir vs cacheDir
     * - filesDir: Almacenamiento interno privado, persistente hasta desinstalar app
     * - cacheDir: Temporal, el sistema puede borrarlo si necesita espacio
     *
     * Usamos filesDir porque queremos que las notas de voz persistan.
     */
    private val audioNotesDir: File by lazy {
        File(context.filesDir, "audio_notes").apply {
            if (!exists()) {
                mkdirs() // Crea el directorio si no existe
            }
        }
    }

    /**
     * Inicia una nueva grabación de audio
     *
     * @return File donde se está guardando el audio
     * @throws IllegalStateException si ya hay una grabación en curso
     *
     * NOTA EDUCATIVA:
     * - Generamos un nombre único con UUID para evitar colisiones
     * - Formato .m4a es contenedor MPEG-4 con audio AAC
     * - AAC es más eficiente que MP3 (mejor calidad a mismo bitrate)
     */
    fun startRecording(): File {
        // Verificar que no haya grabación activa
        if (mediaRecorder != null) {
            throw IllegalStateException("Ya hay una grabación en curso")
        }

        // Crear archivo con nombre único
        val fileName = "audio_${UUID.randomUUID()}.m4a"
        val file = File(audioNotesDir, fileName)

        // Crear MediaRecorder según la versión de Android
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requiere pasar Context al constructor
            MediaRecorder(context)
        } else {
            // Android 11 y anteriores
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        // CONFIGURACIÓN EN ORDEN ESTRICTO (¡importante!)
        recorder.apply {
            // 1. Fuente de audio (micrófono)
            setAudioSource(MediaRecorder.AudioSource.MIC)

            // 2. Formato del contenedor (MPEG-4)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            // 3. Codec de compresión (AAC - Advanced Audio Coding)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            // 4. Calidad del audio
            // 128 kbps = equilibrio entre calidad y tamaño de archivo
            // Comparación: música streaming = 128-320 kbps
            setAudioEncodingBitRate(128_000)

            // 5. Frecuencia de muestreo
            // 44.1 kHz = calidad CD (suficiente para voz humana)
            // Voz telefónica = 8 kHz, Voz clara = 16 kHz, CD = 44.1 kHz
            setAudioSamplingRate(44_100)

            // 6. Archivo de salida
            setOutputFile(file.absolutePath)

            // 7. Preparar el recorder (inicializa hardware)
            prepare()

            // 8. Iniciar grabación
            start()
        }

        // Guardar referencias
        mediaRecorder = recorder
        outputFile = file
        startTimestamp = System.currentTimeMillis()

        return file
    }

    /**
     * Detiene la grabación actual y devuelve información del resultado
     *
     * @return RecordingResult con el archivo y duración
     * @throws IllegalStateException si no hay grabación activa
     *
     * CONCEPTO: Data class
     * RecordingResult es una data class que agrupa información relacionada.
     * Kotlin las genera automáticamente con equals(), hashCode(), toString(), copy()
     */
    fun stopRecording(): RecordingResult {
        val recorder = mediaRecorder
            ?: throw IllegalStateException("No hay grabación activa")

        return try {
            // Detener grabación (escribe metadatos finales al archivo)
            recorder.stop()

            // Calcular duración
            val duration = System.currentTimeMillis() - startTimestamp

            // Obtener referencia al archivo
            val file = outputFile
                ?: throw IllegalStateException("Archivo de salida perdido")

            RecordingResult(file, duration)

        } finally {
            // IMPORTANTE: Siempre liberar recursos en finally
            // Esto garantiza cleanup incluso si stop() lanza excepción
            recorder.reset()
            recorder.release()

            // Limpiar estado
            mediaRecorder = null
            outputFile = null
            startTimestamp = 0L
        }
    }

    /**
     * Cancela la grabación actual sin guardar
     *
     * USO: Si el usuario presiona "cancelar" o cierra la app durante grabación
     *
     * CONCEPTO: Manejo seguro de excepciones
     * Usamos try-catch porque stop() puede fallar si la grabación fue muy corta
     */
    fun cancelRecording() {
        mediaRecorder?.let { recorder ->
            try {
                // Intentar detener (puede fallar si grabación < 1 segundo)
                recorder.stop()
            } catch (e: Exception) {
                // Ignorar errores al cancelar
            } finally {
                recorder.reset()
                recorder.release()
            }
        }

        // Eliminar el archivo si se creó
        outputFile?.delete()

        // Limpiar estado
        mediaRecorder = null
        outputFile = null
        startTimestamp = 0L
    }

    /**
     * Verifica si hay una grabación en curso
     *
     * ÚTIL PARA: Deshabilitar botones en la UI, validaciones
     */
    fun isRecording(): Boolean {
        return mediaRecorder != null
    }

    /**
     * Obtiene la duración actual de la grabación en milisegundos
     *
     * ÚTIL PARA: Mostrar un timer en la UI tipo "00:15" mientras graba
     */
    fun getCurrentDuration(): Long {
        return if (mediaRecorder != null) {
            System.currentTimeMillis() - startTimestamp
        } else {
            0L
        }
    }

    /**
     * Data class que representa el resultado de una grabación completada
     *
     * @property file Archivo .m4a con el audio grabado
     * @property durationMillis Duración total en milisegundos
     *
     * EJEMPLO DE USO:
     * val result = audioRecorder.stopRecording()
     * println("Grabado: ${result.file.name} (${result.durationMillis}ms)")
     */
    data class RecordingResult(
        val file: File,
        val durationMillis: Long
    )
}
