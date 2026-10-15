package com.example.easyreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "series")
data class Series(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val coverUri: String? = null,
    val folderUri: String? = null // URI SAF de la carpeta vinculada (si existe)
)
