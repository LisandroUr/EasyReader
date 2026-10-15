package com.example.easyreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: Series): Long

    @Update
    suspend fun updateSeries(series: Series)

    @Delete
    suspend fun deleteSeries(series: Series)

    @Query("SELECT * FROM series ORDER BY name ASC")
    fun getAllSeries(): Flow<List<Series>>

    @Query("SELECT * FROM series WHERE id = :id")
    fun getSeriesById(id: Long): Flow<Series?>

    @Query("SELECT * FROM series WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchSeries(query: String): Flow<List<Series>>

    @Query("SELECT * FROM series WHERE name = :name LIMIT 1")
    suspend fun getSeriesByName(name: String): Series?
}
