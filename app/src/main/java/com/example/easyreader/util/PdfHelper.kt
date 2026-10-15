package com.example.easyreader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

object PdfHelper {

    fun generateThumbnail(context: Context, pdfUri: Uri): String? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        var tempFile: File? = null
        try {
            if (pdfUri.scheme == "file") {
                val file = File(pdfUri.path ?: "")
                if (file.exists() && file.canRead()) {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            } else {
                try {
                    pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                } catch (_: Exception) {}
            }

            // If direct file descriptor is null or failed, fallback to buffering stream to cache
            if (pfd == null) {
                try {
                    context.contentResolver.openInputStream(pdfUri)?.use { input ->
                        val cache = File(context.cacheDir, "thumb_temp_${abs(pdfUri.toString().hashCode())}.pdf")
                        cache.outputStream().use { output -> input.copyTo(output) }
                        if (cache.exists() && cache.length() > 0) {
                            tempFile = cache
                            pfd = ParcelFileDescriptor.open(cache, ParcelFileDescriptor.MODE_READ_ONLY)
                        }
                    }
                } catch (_: Exception) {}
            }

            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val fileName = "thumb_${System.currentTimeMillis()}_${abs(pdfUri.toString().hashCode())}.png"
                    val file = File(context.filesDir, fileName)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    return Uri.fromFile(file).toString()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try { tempFile?.delete() } catch (_: Exception) {}
        }
        return null
    }
}

