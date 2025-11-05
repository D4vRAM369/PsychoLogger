package com.d4vram.psychologger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * PhotoManager - Gestiona el almacenamiento de fotos asociadas a registros
 *
 * - Las imágenes se guardan en `context.getExternalFilesDir("entry_photos")`
 *   para que sean accesibles sin root desde un gestor de archivos.
 * - Ofrece utilidades para crear archivos, copiar imágenes, generar previsualizaciones
 *   y obtener URIs compartibles mediante FileProvider.
 */
class PhotoManager(private val context: Context) {

    private val photoDir: File by lazy {
        val externalDir = context.getExternalFilesDir("entry_photos")?.apply {
            if (!exists()) mkdirs()
        }

        externalDir ?: File(context.filesDir, "entry_photos").apply {
            if (!exists()) mkdirs()
        }
    }

    private val dateFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun createPhotoFile(): File {
        val timestamp = dateFormatter.format(Date())
        val fileName = "photo_${timestamp}_${UUID.randomUUID()}".plus(".jpg")
        return File(photoDir, fileName)
    }

    fun copyUriToPhoto(uri: Uri): File {
        val extension = guessExtension(uri) ?: "jpg"
        val timestamp = dateFormatter.format(Date())
        val fileName = "photo_${timestamp}_${UUID.randomUUID()}.$extension"
        val target = File(photoDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("No se pudo leer la imagen de origen")

        return target
    }

    fun deletePhoto(filename: String): Boolean {
        val file = File(photoDir, filename)
        return file.exists() && file.delete()
    }

    fun getPreviewDataUrl(filename: String, maxSize: Int = 600): String? {
        val file = File(photoDir, filename)
        if (!file.exists()) return null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val (width, height) = options.outWidth to options.outHeight
        if (width <= 0 || height <= 0) return null

        options.inJustDecodeBounds = false
        options.inSampleSize = calculateInSampleSize(width, height, maxSize)

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    fun getShareUri(filename: String): Uri? {
        val file = File(photoDir, filename)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun guessExtension(uri: Uri): String? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        if (!mime.isNullOrBlank()) {
            val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            if (!extension.isNullOrBlank()) return extension
        }

        val path = uri.lastPathSegment ?: return null
        val dotIndex = path.lastIndexOf('.')
        return if (dotIndex != -1 && dotIndex < path.length - 1) {
            path.substring(dotIndex + 1)
        } else {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sampleSize = 1
        var adjustedWidth = width
        var adjustedHeight = height

        while (adjustedWidth > maxSize || adjustedHeight > maxSize) {
            adjustedWidth /= 2
            adjustedHeight /= 2
            sampleSize *= 2
        }

        return if (sampleSize <= 0) 1 else sampleSize
    }
}

