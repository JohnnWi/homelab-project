package com.homelab.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.homelab.app.data.model.Bookmark
import com.homelab.app.data.model.Category
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

val Context.bookmarksDataStore: DataStore<Preferences> by preferencesDataStore(name = "bookmarks")

@Singleton
class BookmarksRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json
) {
    private val dataStore = context.bookmarksDataStore

    private val CATEGORIES_KEY = stringPreferencesKey("categories_json")
    private val BOOKMARKS_KEY = stringPreferencesKey("bookmarks_json")

    val categories: Flow<List<Category>> = dataStore.data.map { preferences ->
        val jsonStr = preferences[CATEGORIES_KEY] ?: "[]"
        try {
            json.decodeFromString<List<Category>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val bookmarks: Flow<List<Bookmark>> = dataStore.data.map { preferences ->
        val jsonStr = preferences[BOOKMARKS_KEY] ?: "[]"
        try {
            json.decodeFromString<List<Bookmark>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveCategories(categories: List<Category>) {
        val jsonStr = json.encodeToString(categories)
        dataStore.edit { preferences ->
            preferences[CATEGORIES_KEY] = jsonStr
        }
    }

    suspend fun saveBookmarks(bookmarks: List<Bookmark>) {
        val jsonStr = json.encodeToString(bookmarks)
        dataStore.edit { preferences ->
            preferences[BOOKMARKS_KEY] = jsonStr
        }
    }
}
