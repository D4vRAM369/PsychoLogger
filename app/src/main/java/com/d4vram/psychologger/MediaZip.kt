package com.d4vram.psychologger

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Empaquetado de archivos multimedia en ZIP con progreso. Sin dependencias de Android → testeable en JVM. */
object MediaZip {
    /**
     * Escribe [files] como `<prefix>/<nombre>` en [out] (sin cerrarlo).
     * [onProgress] recibe 0..100 según bytes procesados. Devuelve cuántos archivos entraron.
     */
    fun write(files: List<File>, prefix: String, out: OutputStream, onProgress: (Int) -> Unit = {}): Int {
        val total = files.sumOf { it.length() }.coerceAtLeast(1L)
        var done = 0L
        var added = 0
        val zip = ZipOutputStream(out)
        files.forEach { f ->
            try {
                f.inputStream().use { input ->
                    zip.putNextEntry(ZipEntry("$prefix/${f.name}"))
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        zip.write(buf, 0, n)
                        done += n
                        onProgress((done * 100 / total).toInt())
                    }
                }
                zip.closeEntry()
                added++
            } catch (e: java.io.FileNotFoundException) {
                // archivo borrado mientras se exportaba: se omite
            }
        }
        zip.finish() // cierra el ZIP sin cerrar el stream de salida
        return added
    }

    /** Cuenta bytes leídos para reportar progreso de cifrado. */
    class CountingInput(input: InputStream, private val total: Long, private val onProgress: (Int) -> Unit) :
        FilterInputStream(input) {
        private var read = 0L
        private fun bump(n: Int) {
            if (n > 0) { read += n; onProgress((read * 100 / total.coerceAtLeast(1L)).toInt().coerceAtMost(100)) }
        }
        override fun read(): Int = super.read().also { if (it >= 0) bump(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { bump(it) }
    }
}
