package com.example.easyreader.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.net.URLDecoder

object BookFormatUtils {

    fun decodeSafe(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return try {
            URLDecoder.decode(input, "UTF-8")
        } catch (_: Exception) {
            input
        }
    }

    fun extensionFromName(name: String?): String {
        if (name.isNullOrBlank()) return ""
        val decoded = decodeSafe(name)
        val clean = decoded.substringAfterLast('/').substringAfterLast(':').substringBefore('?').substringBefore('#')
        return clean.substringAfterLast('.', missingDelimiterValue = "").lowercase().trim()
    }

    fun isPdfMimeType(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        val lower = mimeType.lowercase().trim()
        return lower == "application/pdf" ||
                lower == "application/x-pdf" ||
                lower == "application/acrobat" ||
                lower == "applications/vnd.pdf" ||
                lower == "text/pdf" ||
                lower == "text/x-pdf"
    }

    fun isPdf(mimeType: String?, name: String?): Boolean {
        if (isPdfMimeType(mimeType)) return true
        val ext = extensionFromName(name)
        if (ext == "pdf") return true
        if (name != null) {
            val decoded = decodeSafe(name)
            val clean = decoded.substringBefore('?').substringBefore('#').lowercase()
            if (clean.endsWith(".pdf") || clean.contains(".pdf/") || clean.contains(".pdf:") || clean.contains(".pdf%")) {
                return true
            }
        }
        return false
    }

    fun isPdfUri(context: Context, uri: Uri): Boolean {
        val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        if (isPdfMimeType(mimeType)) return true

        val docName = try {
            DocumentFile.fromSingleUri(context, uri)?.name
        } catch (_: Exception) { null }

        if (isPdf(mimeType, docName)) return true
        if (isPdf(mimeType, uri.lastPathSegment)) return true
        if (isPdf(mimeType, uri.toString())) return true

        // Check magic bytes %PDF- (0x25, 0x50, 0x44, 0x46)
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(5)
                val bytesRead = stream.read(header)
                if (bytesRead >= 4 &&
                    header[0] == 0x25.toByte() &&
                    header[1] == 0x50.toByte() &&
                    header[2] == 0x44.toByte() &&
                    header[3] == 0x46.toByte()
                ) {
                    return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    fun isSupported(mimeType: String?, name: String?): Boolean {
        return isPdf(mimeType, name)
    }

    fun typeLabelFromUri(uriString: String): String {
        return if (isPdf(null, uriString)) "PDF" else "DOC"
    }
}


