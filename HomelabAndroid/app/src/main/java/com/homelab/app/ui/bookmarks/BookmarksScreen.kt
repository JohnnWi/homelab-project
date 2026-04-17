package com.homelab.app.ui.bookmarks

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homelab.app.R
import com.homelab.app.data.model.Bookmark
import com.homelab.app.data.model.Category
import com.homelab.app.data.model.IconType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookmarksScreen(
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var bookmarkToEdit by remember { mutableStateOf<Bookmark?>(null) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }

    var showBookmarkForm by rememberSaveable { mutableStateOf(false) }
    var showCategoryForm by rememberSaveable { mutableStateOf(false) }
    var preselectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var showReorderDialog by rememberSaveable { mutableStateOf(false) }
    var collapsedCategoryIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    FilledTonalIconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = stringResource(R.string.bookmark_toggle_view)
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { showReorderDialog = true },
                        enabled = uiState.categories.isNotEmpty()
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.bookmark_reorder))
                    }
                    FilledTonalIconButton(onClick = {
                        categoryToEdit = null
                        showCategoryForm = true
                    }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.category_add))
                    }
                    FilledTonalIconButton(
                        onClick = {
                            bookmarkToEdit = null
                            preselectedCategoryId = null
                            showBookmarkForm = true
                        },
                        enabled = uiState.categories.isNotEmpty()
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = stringResource(R.string.bookmark_add))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            Text(
                text = stringResource(R.string.nav_bookmarks),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.bookmark_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.bookmark_search), modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.size(20.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.categories.isEmpty()) {
                // Empty state
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.category_empty),
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.category_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = {
                            categoryToEdit = null
                            showCategoryForm = true
                        },
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(stringResource(R.string.category_add))
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = if (isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    uiState.categories.forEach { category ->
                        val categoryColor = category.color?.let { hex ->
                            try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) { null }
                        }

                        val categoryBookmarks = uiState.bookmarks.filter { bookmark ->
                            bookmark.categoryId == category.id &&
                            (searchQuery.isBlank() ||
                             bookmark.title.contains(searchQuery, ignoreCase = true) ||
                             bookmark.description.contains(searchQuery, ignoreCase = true) ||
                             bookmark.tags.any { it.contains(searchQuery, ignoreCase = true) } ||
                             bookmark.url.contains(searchQuery, ignoreCase = true))
                        }

                        if (categoryBookmarks.isNotEmpty() || searchQuery.isBlank()) {
                            // Header
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CategoryHeader(
                                    category = category,
                                    bookmarkCount = categoryBookmarks.size,
                                    categoryColor = categoryColor,
                                    isCollapsed = collapsedCategoryIds.contains(category.id),
                                    onToggle = {
                                        collapsedCategoryIds = if (collapsedCategoryIds.contains(category.id)) {
                                            collapsedCategoryIds - category.id
                                        } else {
                                            collapsedCategoryIds + category.id
                                        }
                                    },
                                    onEdit = {
                                        categoryToEdit = category
                                        showCategoryForm = true
                                    },
                                    onAddBookmark = {
                                        bookmarkToEdit = null
                                        preselectedCategoryId = category.id
                                        showBookmarkForm = true
                                    }
                                )
                            }

                            if (categoryBookmarks.isNotEmpty()) {
                                val isCollapsed = collapsedCategoryIds.contains(category.id)
                                if (isGridView) {
                                    items(
                                        items = categoryBookmarks,
                                        key = { it.id },
                                        span = { GridItemSpan(1) }
                                    ) { bookmark ->
                                        AnimatedVisibility(
                                            visible = !isCollapsed,
                                            enter = expandVertically(
                                                animationSpec = tween(durationMillis = 260),
                                                expandFrom = Alignment.Top
                                            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                                            exit = shrinkVertically(
                                                animationSpec = tween(durationMillis = 220),
                                                shrinkTowards = Alignment.Top
                                            ) + fadeOut(animationSpec = tween(durationMillis = 150))
                                        ) {
                                            BookmarkGridCard(
                                                bookmark = bookmark,
                                                accentColor = categoryColor ?: MaterialTheme.colorScheme.primary,
                                                onClick = {
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizeWebUrl(bookmark.url))))
                                                    } catch (_: Exception) {}
                                                },
                                                onEdit = {
                                                    bookmarkToEdit = bookmark
                                                    showBookmarkForm = true
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    itemsIndexed(
                                        items = categoryBookmarks,
                                        key = { _, item -> item.id },
                                        span = { _, _ -> GridItemSpan(maxLineSpan) }
                                    ) { index, bookmark ->
                                        AnimatedVisibility(
                                            visible = !isCollapsed,
                                            enter = expandVertically(
                                                animationSpec = tween(durationMillis = 260),
                                                expandFrom = Alignment.Top
                                            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                                            exit = shrinkVertically(
                                                animationSpec = tween(durationMillis = 220),
                                                shrinkTowards = Alignment.Top
                                            ) + fadeOut(animationSpec = tween(durationMillis = 150))
                                        ) {
                                            BookmarkListCard(
                                                bookmark = bookmark,
                                                accentColor = categoryColor ?: MaterialTheme.colorScheme.primary,
                                                onClick = {
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizeWebUrl(bookmark.url))))
                                                    } catch (_: Exception) {}
                                                },
                                                onEdit = {
                                                    bookmarkToEdit = bookmark
                                                    showBookmarkForm = true
                                                }
                                            )

                                            if (index < categoryBookmarks.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = 46.dp, end = 8.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCategoryForm) {
        CategoryFormDialog(
            category = categoryToEdit,
            onDismiss = { showCategoryForm = false },
            onSave = { name, icon, color ->
                if (categoryToEdit == null) {
                    viewModel.addCategory(name, icon, color)
                } else {
                    viewModel.updateCategory(categoryToEdit!!.copy(name = name, icon = icon, color = color))
                }
                showCategoryForm = false
            },
            onDelete = { categoryId ->
                viewModel.deleteCategory(categoryId)
            }
        )
    }

    if (showBookmarkForm) {
        BookmarkFormDialog(
            bookmark = bookmarkToEdit,
            categories = uiState.categories,
            selectedCategoryId = preselectedCategoryId,
            onDismiss = { showBookmarkForm = false },
            onSave = { bookmark ->
                if (bookmarkToEdit == null) {
                    viewModel.addBookmark(bookmark)
                } else {
                    viewModel.updateBookmark(bookmark)
                }
                showBookmarkForm = false
            },
            onDelete = { bookmarkId ->
                viewModel.deleteBookmark(bookmarkId)
            }
        )
    }

    if (showReorderDialog) {
        BookmarksReorderDialog(
            categories = uiState.categories,
            bookmarks = uiState.bookmarks,
            onDismiss = { showReorderDialog = false },
            onMoveCategory = { from, to ->
                viewModel.reorderCategories(from, to)
            },
            onMoveBookmark = { categoryId, from, to ->
                viewModel.reorderBookmarks(categoryId, from, to)
            }
        )
    }
}

@Composable
fun CategoryHeader(
    category: Category,
    bookmarkCount: Int,
    categoryColor: Color?,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onAddBookmark: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggle)
                    .padding(end = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(categoryColor ?: MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(10.dp))

                val categoryIcon = categoryIconForId(category.icon)
                if (categoryIcon != null) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background((categoryColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = category.name,
                            tint = categoryColor ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$bookmarkCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = stringResource(
                        if (isCollapsed) R.string.bookmark_expand_category else R.string.bookmark_collapse_category
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row {
                IconButton(onClick = onAddBookmark, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.bookmark_add), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.category_edit), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun BookmarkIcon(bookmark: Bookmark, accentColor: Color, size: Int = 28) {
    when (bookmark.iconType) {
        IconType.FAVICON -> {
            val candidates = buildFaviconCandidates(bookmark.url)
            FallbackRemoteIcon(
                urls = candidates,
                modifier = Modifier.size(size.dp),
                contentDescription = stringResource(R.string.bookmark_icon),
                loading = {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = stringResource(R.string.bookmark_icon),
                        tint = accentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(size.dp)
                    )
                },
                fallback = {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = stringResource(R.string.bookmark_icon),
                        tint = accentColor,
                        modifier = Modifier.size(size.dp)
                    )
                }
            )
        }
        IconType.SELFHST -> {
            val selfhstCandidate = selfhstIconUrl(bookmark.iconValue)
            FallbackRemoteIcon(
                urls = listOfNotNull(selfhstCandidate),
                modifier = Modifier.size(size.dp),
                contentDescription = stringResource(R.string.bookmark_icon),
                loading = {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = stringResource(R.string.bookmark_icon),
                        tint = accentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(size.dp)
                    )
                },
                fallback = {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = stringResource(R.string.bookmark_icon),
                        tint = accentColor,
                        modifier = Modifier.size(size.dp)
                    )
                }
            )
        }
        IconType.SYSTEM_SYMBOL -> {
            val imageCandidate = normalizeRemoteImageUrl(bookmark.iconValue)
            FallbackRemoteIcon(
                urls = listOfNotNull(imageCandidate),
                modifier = Modifier.size(size.dp),
                contentDescription = stringResource(R.string.bookmark_icon),
                loading = {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = stringResource(R.string.bookmark_icon),
                        tint = accentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(size.dp)
                    )
                },
                fallback = {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = stringResource(R.string.bookmark_icon),
                        tint = accentColor,
                        modifier = Modifier.size(size.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun BookmarkListCard(
    bookmark: Bookmark,
    accentColor: Color,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookmarkIcon(bookmark = bookmark, accentColor = accentColor)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (bookmark.description.isNotBlank()) {
                Text(
                    text = bookmark.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val host = extractHost(bookmark.url) ?: ""
            if (host.isNotEmpty()) {
                Text(
                    text = host,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (bookmark.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    bookmark.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (bookmark.tags.size > 3) {
                        Text(
                            text = "+${bookmark.tags.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
        }

        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun BookmarkGridCard(
    bookmark: Bookmark,
    accentColor: Color,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            BookmarkIcon(bookmark = bookmark, accentColor = accentColor, size = 22)

            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = bookmark.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val host = extractHost(bookmark.url) ?: ""
        if (host.isNotEmpty()) {
            Text(
                text = host,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (bookmark.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                bookmark.tags.take(2).forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksReorderDialog(
    categories: List<Category>,
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onMoveCategory: (from: Int, to: Int) -> Unit,
    onMoveBookmark: (categoryId: String, from: Int, to: Int) -> Unit
) {
    val orderedCategories = remember(categories) { categories.sortedBy { it.sortOrder } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmark_reorder)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                orderedCategories.forEachIndexed { categoryIndex, category ->
                    val categoryBookmarks = bookmarks
                        .filter { it.categoryId == category.id }
                        .sortedBy { it.sortOrder }
                    val categoryColor = category.color?.let { hex ->
                        try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { null }
                    } ?: MaterialTheme.colorScheme.primary

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(22.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(categoryColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = categoryIconForId(category.icon) ?: Icons.Default.CreateNewFolder,
                                    contentDescription = stringResource(R.string.category_icon),
                                    tint = categoryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.bookmark_reorder_category_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { onMoveCategory(categoryIndex, categoryIndex - 1) },
                                    enabled = categoryIndex > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.settings_move_up), modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { onMoveCategory(categoryIndex, categoryIndex + 1) },
                                    enabled = categoryIndex < orderedCategories.lastIndex,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.settings_move_down), modifier = Modifier.size(16.dp))
                                }
                            }

                            if (categoryBookmarks.isEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                            } else {
                                categoryBookmarks.forEachIndexed { bookmarkIndex, bookmark ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 18.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragIndicator,
                                                contentDescription = stringResource(R.string.bookmark_drag_handle),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = bookmark.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.bookmark_reorder_bookmark_label),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { onMoveBookmark(category.id, bookmarkIndex, bookmarkIndex - 1) },
                                                enabled = bookmarkIndex > 0,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.settings_move_up), modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { onMoveBookmark(category.id, bookmarkIndex, bookmarkIndex + 1) },
                                                enabled = bookmarkIndex < categoryBookmarks.lastIndex,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.settings_move_down), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
