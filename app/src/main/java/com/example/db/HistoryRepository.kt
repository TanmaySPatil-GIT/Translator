package com.example.db

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<HistoryItem>> = historyDao.getAllHistory()
    val favoriteHistory: Flow<List<HistoryItem>> = historyDao.getFavoriteHistory()

    suspend fun insert(item: HistoryItem) {
        historyDao.insertHistoryItem(item)
    }

    suspend fun delete(id: Int) {
        historyDao.deleteHistoryItemById(id)
    }

    suspend fun clearAll() {
        historyDao.clearAllHistory()
    }

    suspend fun updateFavorite(id: Int, isFavorite: Boolean) {
        historyDao.updateFavoriteStatus(id, isFavorite)
    }

    suspend fun findCachedTranslation(originalText: String, sourceLanguageCode: String, targetLanguageCode: String): HistoryItem? {
        return historyDao.findCachedTranslation(originalText, sourceLanguageCode, targetLanguageCode)
    }
}
