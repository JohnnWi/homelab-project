package com.homelab.app.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.model.Bookmark
import com.homelab.app.data.model.Category
import com.homelab.app.data.repository.BookmarksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class BookmarksUiState(
    val categories: List<Category> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarksRepository
) : ViewModel() {

    val uiState: StateFlow<BookmarksUiState> = combine(
        repository.categories,
        repository.bookmarks
    ) { categories, bookmarks ->
        BookmarksUiState(
            categories = categories.sortedBy { it.sortOrder },
            bookmarks = bookmarks.sortedBy { it.sortOrder },
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookmarksUiState()
    )

    fun addCategory(name: String, icon: String, color: String?) {
        viewModelScope.launch {
            val current = uiState.value.categories.toMutableList()
            val newCategory = Category(
                id = UUID.randomUUID().toString(),
                name = name,
                icon = icon,
                color = color,
                sortOrder = current.size
            )
            current.add(newCategory)
            repository.saveCategories(current)
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            val current = uiState.value.categories.toMutableList()
            val index = current.indexOfFirst { it.id == category.id }
            if (index != -1) {
                current[index] = category
                repository.saveCategories(current)
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            val currentCategories = uiState.value.categories.filter { it.id != categoryId }
            repository.saveCategories(currentCategories)

            // Also delete associated bookmarks
            val currentBookmarks = uiState.value.bookmarks.filter { it.categoryId != categoryId }
            repository.saveBookmarks(currentBookmarks)
        }
    }

    fun addBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            val current = uiState.value.bookmarks.toMutableList()
            // Set sort order to be at the end of its category
            val categoryBookmarksCount = current.count { it.categoryId == bookmark.categoryId }
            val newBookmark = bookmark.copy(sortOrder = categoryBookmarksCount)
            current.add(newBookmark)
            repository.saveBookmarks(current)
        }
    }

    fun updateBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            val current = uiState.value.bookmarks.toMutableList()
            val index = current.indexOfFirst { it.id == bookmark.id }
            if (index != -1) {
                val oldBookmark = current[index]
                var updatedBookmark = bookmark

                if (oldBookmark.categoryId != bookmark.categoryId) {
                    val targetMaxSort = current
                        .filter { it.categoryId == bookmark.categoryId && it.id != bookmark.id }
                        .maxOfOrNull { it.sortOrder }
                        ?.plus(1) ?: 0
                    updatedBookmark = bookmark.copy(sortOrder = targetMaxSort)
                }

                current[index] = updatedBookmark

                if (oldBookmark.categoryId != updatedBookmark.categoryId) {
                    normalizeCategorySortOrders(current, oldBookmark.categoryId)
                    normalizeCategorySortOrders(current, updatedBookmark.categoryId)
                } else {
                    normalizeCategorySortOrders(current, updatedBookmark.categoryId)
                }

                repository.saveBookmarks(current)
            }
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            val current = uiState.value.bookmarks.filter { it.id != bookmarkId }
            repository.saveBookmarks(current)
        }
    }
    
    fun reorderCategories(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val current = uiState.value.categories.toMutableList()
            if (fromIndex in current.indices && toIndex in current.indices) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                
                // Update sortOrder for all items
                val updated = current.mapIndexed { index, category -> 
                    category.copy(sortOrder = index)
                }
                repository.saveCategories(updated)
            }
        }
    }
    
    fun reorderBookmarks(categoryId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentBookmarks = uiState.value.bookmarks.toMutableList()
            
            // Extract bookmarks for this category and sort them by current order
            val categoryBookmarks = currentBookmarks.filter { it.categoryId == categoryId }.sortedBy { it.sortOrder }.toMutableList()
            
            if (fromIndex in categoryBookmarks.indices && toIndex in categoryBookmarks.indices) {
                val item = categoryBookmarks.removeAt(fromIndex)
                categoryBookmarks.add(toIndex, item)
                
                // Update sortOrders for affected bookmarks
                val updatedCategoryBookmarks = categoryBookmarks.mapIndexed { index, bookmark ->
                    bookmark.copy(sortOrder = index)
                }
                
                // Replace old bookmarks with updated ones
                currentBookmarks.removeAll { it.categoryId == categoryId }
                currentBookmarks.addAll(updatedCategoryBookmarks)
                
                repository.saveBookmarks(currentBookmarks)
            }
        }
    }

    private fun normalizeCategorySortOrders(bookmarks: MutableList<Bookmark>, categoryId: String) {
        val ordered = bookmarks
            .filter { it.categoryId == categoryId }
            .sortedBy { it.sortOrder }

        ordered.forEachIndexed { index, bookmark ->
            val i = bookmarks.indexOfFirst { it.id == bookmark.id }
            if (i != -1) {
                bookmarks[i] = bookmarks[i].copy(sortOrder = index)
            }
        }
    }
}
