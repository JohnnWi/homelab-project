package com.homelab.app.ui.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import coil3.compose.SubcomposeAsyncImage
import com.homelab.app.R
import com.homelab.app.data.model.Bookmark
import com.homelab.app.data.model.Category
import com.homelab.app.data.model.IconType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkFormDialog(
    bookmark: Bookmark? = null,
    categories: List<Category>,
    selectedCategoryId: String? = null,
    onDismiss: () -> Unit,
    onSave: (Bookmark) -> Unit,
    onDelete: (String) -> Unit
) {
    if (categories.isEmpty()) return

    val initialCategoryId = bookmark?.categoryId ?: selectedCategoryId ?: categories.first().id
    val isEdit = bookmark != null

    var title by remember { mutableStateOf(bookmark?.title ?: "") }
    var description by remember { mutableStateOf(bookmark?.description ?: "") }
    var url by remember { mutableStateOf(bookmark?.url ?: "https://") }
    var tagsText by remember { mutableStateOf(bookmark?.tags?.joinToString(", ") ?: "") }
    var categoryId by remember { mutableStateOf(initialCategoryId) }

    var iconType by remember { mutableStateOf(bookmark?.iconType ?: IconType.FAVICON) }
    var selfhstService by remember {
        mutableStateOf(
            if (bookmark?.iconType == IconType.SELFHST) bookmark.iconValue else ""
        )
    }
    var imageUrl by remember {
        mutableStateOf(
            if (bookmark?.iconType == IconType.SYSTEM_SYMBOL) bookmark.iconValue else ""
        )
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val selectedCategoryName = categories.find { it.id == categoryId }?.name ?: ""
    val normalizedUrl = remember(url) { normalizeWebUrl(url) }
    val host = remember(normalizedUrl) { extractHost(normalizedUrl) }
    val faviconCandidates = remember(normalizedUrl) { buildFaviconCandidates(normalizedUrl) }
    val normalizedSelfhst = remember(selfhstService) { extractSelfhstService(selfhstService) }
    val normalizedImageUrl = remember(imageUrl) { normalizeRemoteImageUrl(imageUrl) }
    val selfhstSuggestions = remember(normalizedSelfhst) {
        if (normalizedSelfhst.isBlank()) {
            selfhstServices.take(12)
        } else {
            selfhstServices.filter { it.contains(normalizedSelfhst) }.take(10)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(if (isEdit) R.string.bookmark_edit else R.string.bookmark_add))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.bookmark_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.bookmark_desc)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(stringResource(R.string.bookmark_tags)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.bookmark_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.bookmark_category),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { categoryMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = selectedCategoryName,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (categoryMenuExpanded) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null
                            )
                        }
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        categoryId = cat.id
                                        categoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    stringResource(R.string.bookmark_icon_type),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = iconType == IconType.FAVICON,
                        onClick = { iconType = IconType.FAVICON },
                        label = { Text(stringResource(R.string.bookmark_favicon)) }
                    )
                    FilterChip(
                        selected = iconType == IconType.SELFHST,
                        onClick = { iconType = IconType.SELFHST },
                        label = { Text(stringResource(R.string.bookmark_selfhst)) }
                    )
                    FilterChip(
                        selected = iconType == IconType.SYSTEM_SYMBOL,
                        onClick = { iconType = IconType.SYSTEM_SYMBOL },
                        label = { Text(stringResource(R.string.bookmark_symbol)) }
                    )
                }

                when (iconType) {
                    IconType.FAVICON -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FallbackRemoteIcon(
                                urls = faviconCandidates,
                                modifier = Modifier.size(36.dp),
                                loading = {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                fallback = {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                            Text(
                                text = host?.let { "${stringResource(R.string.bookmark_auto_favicon)} ($it)" }
                                    ?: stringResource(R.string.bookmark_enter_url),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconType.SELFHST -> {
                        OutlinedTextField(
                            value = selfhstService,
                            onValueChange = { selfhstService = it },
                            label = { Text(stringResource(R.string.bookmark_selfhst_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (selfhstSuggestions.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selfhstSuggestions.forEach { suggestion ->
                                    FilterChip(
                                        selected = normalizedSelfhst == suggestion,
                                        onClick = { selfhstService = suggestion },
                                        label = { Text(suggestion) }
                                    )
                                }
                            }
                        }

                        selfhstIconUrl(normalizedSelfhst)?.let { preview ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SubcomposeAsyncImage(
                                    model = preview,
                                    contentDescription = stringResource(R.string.preview),
                                    modifier = Modifier.size(36.dp),
                                    loading = {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    },
                                    error = {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                )
                                Text(
                                    stringResource(R.string.bookmark_selfhst_preview),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IconType.SYSTEM_SYMBOL -> {
                        OutlinedTextField(
                            value = imageUrl,
                            onValueChange = { imageUrl = it },
                            label = { Text(stringResource(R.string.bookmark_enter_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!normalizedImageUrl.isNullOrBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SubcomposeAsyncImage(
                                    model = normalizedImageUrl,
                                    contentDescription = stringResource(R.string.preview),
                                    modifier = Modifier.size(36.dp),
                                    loading = {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    },
                                    error = {
                                        Icon(
                                            Icons.Default.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                )
                                Text(
                                    text = normalizedImageUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (isEdit) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank() && normalizedUrl.isNotBlank()) {
                        val parsedTags = tagsText.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }

                        val iconValue = when (iconType) {
                            IconType.FAVICON -> ""
                            IconType.SELFHST -> normalizedSelfhst
                            IconType.SYSTEM_SYMBOL -> normalizeRemoteImageUrl(imageUrl) ?: imageUrl.trim()
                        }

                        val newBookmark = Bookmark(
                            id = bookmark?.id ?: java.util.UUID.randomUUID().toString(),
                            categoryId = categoryId,
                            title = title.trim(),
                            description = description.trim(),
                            url = normalizedUrl,
                            iconType = iconType,
                            iconValue = iconValue,
                            tags = parsedTags,
                            sortOrder = bookmark?.sortOrder ?: 0
                        )
                        onSave(newBookmark)
                    }
                },
                enabled = title.isNotBlank() && normalizedUrl.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    if (showDeleteConfirm && bookmark != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.bookmark_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(bookmark.id)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
