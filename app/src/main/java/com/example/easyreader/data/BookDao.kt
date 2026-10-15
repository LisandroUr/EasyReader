package com.example.easyreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("SELECT * FROM books WHERE seriesId = :seriesId ORDER BY customOrder ASC")
    fun getBooksBySeries(seriesId: Long): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookById(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE seriesId = :seriesId AND pdfUri = :pdfUri LIMIT 1")
    suspend fun getBookBySeriesAndPdfUri(seriesId: Long, pdfUri: String): Book?

    @Query("SELECT MAX(customOrder) FROM books WHERE seriesId = :seriesId")
    suspend fun getMaxOrder(seriesId: Long): Int?

    @Query("SELECT * FROM books WHERE lastReadTimestamp > 0 ORDER BY lastReadTimestamp DESC LIMIT 10")
    fun getRecentlyReadBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE pdfUri LIKE '%' || :query || '%'")
    fun searchBooks(query: String): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY id DESC")
    fun getAllBooks(): Flow<List<Book>>
}
