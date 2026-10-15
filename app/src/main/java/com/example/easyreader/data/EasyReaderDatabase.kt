package com.example.easyreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Series::class, Book::class], version = 6, exportSchema = false)
abstract class EasyReaderDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var Instance: InstanceHolder? = null

        private class InstanceHolder(val database: EasyReaderDatabase)

        // Migration para agregar columna folderUri a la tabla series
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE series ADD COLUMN folderUri TEXT")
            }
        }

        // Migration para limpiar PDFs duplicados y evitar que una misma ruta se importe dos veces en la misma serie.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM books WHERE rowid NOT IN (SELECT MIN(rowid) FROM books GROUP BY seriesId, pdfUri)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_books_seriesId_pdfUri ON books(seriesId, pdfUri)"
                )
            }
        }

        // Migration para guardar un marcador de lectura manual en books.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN bookmarkPage INTEGER NOT NULL DEFAULT -1")
            }
        }

        fun getDatabase(context: Context): EasyReaderDatabase {
            return Instance?.database ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    EasyReaderDatabase::class.java,
                    "easy_reader_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // Si fallan migraciones desconocidas, podría añadirse fallback; preferimos migraciones para preservar datos.
                    .build()
                Instance = InstanceHolder(db)
                db
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                Instance?.database?.close()
                Instance = null
            }
        }
    }
}
