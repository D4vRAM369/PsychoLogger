package com.d4vram.psychologger

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaZipTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writesEntriesUnderPrefixAndReportsProgress() {
        val a = tmp.newFile("a.jpg").apply { writeBytes(ByteArray(100_000) { 1 }) }
        val b = tmp.newFile("b.jpg").apply { writeBytes(ByteArray(50_000) { 2 }) }
        val out = ByteArrayOutputStream()
        val progress = mutableListOf<Int>()
        val n = MediaZip.write(listOf(a, b), "photos", out) { progress += it }

        assertEquals(2, n)
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { (x, y) -> x <= y })
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) { names += (z.nextEntry ?: break).name }
        }
        assertEquals(listOf("photos/a.jpg", "photos/b.jpg"), names)
    }

    @Test fun skipsFileDeletedMidExport() {
        val a = tmp.newFile("a.jpg").apply { writeBytes(ByteArray(10)) }
        val gone = File(tmp.root, "gone.jpg")
        val n = MediaZip.write(listOf(gone, a), "photos", ByteArrayOutputStream())
        assertEquals(1, n)
    }
}
