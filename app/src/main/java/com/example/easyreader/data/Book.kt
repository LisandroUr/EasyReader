package com.example.easyreader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["seriesId"]),
        Index(value = ["seriesId", "pdfUri"], unique = true)
    ]
)
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val seriesId: Long,
    val pdfUri: String,
    val coverUri: String? = null,
    val currentPage: Int = 0,
    val bookmarkPage: Int = -1,
    val totalPages: Int = 0,
    val customOrder: Int = 0,
    val lastReadTimestamp: Long = 0
)
