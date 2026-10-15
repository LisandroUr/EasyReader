package com.example.easyreader.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SeriesExportMetadata(
    val name: String,
    val books: List<BookExportMetadata>
)

@JsonClass(generateAdapter = true)
data class BookExportMetadata(
    val fileName: String,
    val customOrder: Int,
    val totalPages: Int,
    val pdfFileName: String,
    val coverFileName: String?
)
