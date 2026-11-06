package com.d4vram.psychologger

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM en streaming por bloques ("chunked-v2").
 *
 * POR QUÉ: Conscrypt acumula TODO el texto plano en memoria hasta doFinal() (y
 * lo duplica), así que cifrar un ZIP de 168 MB con un GCM único reventaba el heap
 * (OutOfMemoryError). Aquí cada bloque es un GCM independiente → memoria O(bloque).
 *
 * FORMATO de data.enc: registros [len:4 BE][ciphertext+tag(16)]
 *  - nonce_i = iv (12 B) con los últimos 4 bytes XOR contador i (BE)
 *  - AAD = 1 byte: 1 si es el último bloque, 0 si no → detecta truncado y reordenado
 *  - metadata.json lleva "format":"chunked-v2" y "chunkSize"
 */
object ChunkedCrypto {
    const val FORMAT = "chunked-v2"
    const val DEFAULT_CHUNK = 1024 * 1024
    private const val TAG_BITS = 128
    private const val TAG_BYTES = 16
    private const val MODE = "AES/GCM/NoPadding"

    private fun nonce(iv: ByteArray, counter: Int): ByteArray {
        val n = iv.copyOf()
        val c = ByteBuffer.allocate(4).putInt(counter).array()
        for (i in 0 until 4) n[n.size - 4 + i] = (n[n.size - 4 + i].toInt() xor c[i].toInt()).toByte()
        return n
    }

    private fun cipher(mode: Int, key: SecretKeySpec, iv: ByteArray, counter: Int, last: Boolean): Cipher =
        Cipher.getInstance(MODE).apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce(iv, counter)))
            updateAAD(byteArrayOf(if (last) 1 else 0))
        }

    /** Lee hasta llenar buf; devuelve bytes leídos (< buf.size solo en EOF). */
    private fun fill(input: InputStream, buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    fun encrypt(input: InputStream, output: OutputStream, key: SecretKeySpec, iv: ByteArray, chunkSize: Int = DEFAULT_CHUNK) {
        var cur = ByteArray(chunkSize)
        var next = ByteArray(chunkSize)
        var curLen = fill(input, cur)
        var counter = 0
        while (true) {
            // Lectura adelantada para saber si el bloque actual es el último
            val nextLen = if (curLen == chunkSize) fill(input, next) else 0
            val last = nextLen == 0
            val ct = cipher(Cipher.ENCRYPT_MODE, key, iv, counter, last).doFinal(cur, 0, curLen)
            output.write(ByteBuffer.allocate(4).putInt(ct.size).array())
            output.write(ct)
            if (last) return
            val t = cur; cur = next; next = t
            curLen = nextLen
            counter++
        }
    }

    /** Lanza IOException si la clave es incorrecta, el dato está manipulado o truncado. */
    fun decrypt(input: InputStream, output: OutputStream, key: SecretKeySpec, iv: ByteArray, maxChunk: Int = 64 * 1024 * 1024) {
        val din = DataInputStream(input)
        var counter = 0
        var len = try { din.readInt() } catch (e: EOFException) { throw IOException("Archivo cifrado vacío o corrupto") }
        while (true) {
            if (len < TAG_BYTES || len > maxChunk + TAG_BYTES) throw IOException("Bloque cifrado inválido")
            val ct = ByteArray(len)
            din.readFully(ct)
            val nextLen = try { din.readInt() } catch (e: EOFException) { -1 }
            val last = nextLen == -1
            val plain = try {
                cipher(Cipher.DECRYPT_MODE, key, iv, counter, last).doFinal(ct)
            } catch (e: AEADBadTagException) {
                throw IOException("Contraseña incorrecta o datos corruptos", e)
            }
            output.write(plain)
            if (last) return
            len = nextLen
            counter++
        }
    }
}
