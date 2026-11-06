package com.d4vram.psychologger

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChunkedCryptoTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val iv = ByteArray(12) { (it * 7).toByte() }

    private fun enc(data: ByteArray, chunk: Int = 1024): ByteArray {
        val out = ByteArrayOutputStream()
        ChunkedCrypto.encrypt(ByteArrayInputStream(data), out, key, iv, chunk)
        return out.toByteArray()
    }

    private fun dec(data: ByteArray, k: SecretKeySpec = key): ByteArray {
        val out = ByteArrayOutputStream()
        ChunkedCrypto.decrypt(ByteArrayInputStream(data), out, k, iv)
        return out.toByteArray()
    }

    @Test fun roundTripMultiChunk() {
        val data = ByteArray(5000) { (it % 251).toByte() }
        assertArrayEquals(data, dec(enc(data)))
    }

    @Test fun roundTripExactMultipleOfChunk() {
        val data = ByteArray(3072) { it.toByte() }
        assertArrayEquals(data, dec(enc(data)))
    }

    @Test fun roundTripEmpty() {
        assertArrayEquals(ByteArray(0), dec(enc(ByteArray(0))))
    }

    @Test fun wrongKeyFails() {
        val bad = SecretKeySpec(ByteArray(32) { 9 }, "AES")
        assertThrows(IOException::class.java) { dec(enc(ByteArray(100)), bad) }
    }

    @Test fun truncationDetected() {
        val data = ByteArray(5000) { it.toByte() }
        val e = enc(data)
        // Corta el último bloque entero: debe fallar (flag de bloque final en AAD)
        val firstChunkLen = 4 + 1024 + 16
        assertThrows(IOException::class.java) { dec(e.copyOf(firstChunkLen * 2)) }
    }

    @Test fun tamperDetected() {
        val e = enc(ByteArray(2000) { it.toByte() })
        e[10] = (e[10].toInt() xor 1).toByte()
        assertThrows(IOException::class.java) { dec(e) }
    }

    @Test fun largeStreamDoesNotBufferEverything() {
        // 12 MiB con bloques de 1 MiB: el pico de memoria es ~1 bloque, no 12 MiB
        val data = ByteArray(12 * 1024 * 1024) { (it % 13).toByte() }
        assertArrayEquals(data, dec(enc(data, 1024 * 1024)))
    }
}
