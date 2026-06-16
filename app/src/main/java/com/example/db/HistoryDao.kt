package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM translation_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: HistoryItem)

    @Query("DELETE FROM translation_history WHERE id = :id")
    suspend fun deleteHistoryItemById(id: Int)

    @Query("DELETE FROM translation_history")
    suspend fun clearAllHistory()

    @Query("SELECT * FROM translation_history WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteHistory(): Flow<List<HistoryItem>>

    @Query("UPDATE translation_history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Int, isFavorite: Boolean)

    @Query("SELECT * FROM translation_history WHERE LOWER(TRIM(originalText)) = LOWER(TRIM(:originalText)) AND (sourceLanguageCode = :sourceLanguageCode OR :sourceLanguageCode = 'auto') AND targetLanguageCode = :targetLanguageCode ORDER BY timestamp DESC LIMIT 1")
    suspend fun findCachedTranslation(originalText: String, sourceLanguageCode: String, targetLanguageCode: String): HistoryItem?
}
