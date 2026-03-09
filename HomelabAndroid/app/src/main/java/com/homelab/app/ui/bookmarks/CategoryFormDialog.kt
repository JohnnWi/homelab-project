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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homelab.app.R
import com.homelab.app.data.model.Category

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryFormDialog(
    category: Category? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: String?) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var icon by remember { mutableStateOf(category?.icon ?: "") }
    var color by remember { mutableStateOf(category?.color ?: categoryColorChoices.first().hex) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isEdit = category != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(if (isEdit) R.string.category_edit else R.string.category_add))
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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.bookmark_icon),
                    style = MaterialTheme.typography.titleSmall
                )
                val iconRows = (categoryIconChoices.size + 4) / 5
                val iconGridHeight = (iconRows * 46 + (iconRows - 1) * 10).dp
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(iconGridHeight),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false
                ) {
                    items(categoryIconChoices, key = { it.id }) { choice ->
                        val isSelected = icon == choice.id
                        val selectionScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1f,
                            animationSpec = tween(durationMillis = 140),
                            label = "iconSelectionScale"
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) parseHexColor(color).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.6.dp else 1.dp,
                                color = if (isSelected) parseHexColor(color).copy(alpha = 0.9f) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .size(46.dp)
                                .graphicsLayer {
                                    scaleX = selectionScale
                                    scaleY = selectionScale
                                }
                                .clickable { icon = choice.id }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = choice.icon,
                                    contentDescription = choice.id,
                                    tint = if (isSelected) parseHexColor(color) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                if (icon.isNotBlank()) {
                    RowPreviewIcon(icon = icon, color = color)
                }

                Text(
                    text = stringResource(R.string.category_color),
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryColorChoices.forEach { choice ->
                        val parsed = parseHexColor(choice.hex)
                        val isSelected = color == choice.hex
                        val selectionScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1f,
                            animationSpec = tween(durationMillis = 140),
                            label = "colorSelectionScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    scaleX = selectionScale
                                    scaleY = selectionScale
                                }
                                .clip(CircleShape)
                                .background(parsed)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable {
                                    color = choice.hex
                                }
                        )
                    }
                }
                
                if (isEdit) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.category_delete))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, icon, color)
                    }
                },
                enabled = name.isNotBlank()
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

    if (showDeleteConfirm && category != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.category_delete)) },
            text = { Text(stringResource(R.string.category_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(category.id)
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

@Composable
private fun RowPreviewIcon(icon: String, color: String?) {
    val vector = categoryIconForId(icon) ?: return
    val tint = parseHexColor(color)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.16f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = vector, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
        }
    }
}

private data class CategoryColorChoice(val hex: String)

private val categoryColorChoices = listOf(
    CategoryColorChoice("#007AFF"),
    CategoryColorChoice("#AF52DE"),
    CategoryColorChoice("#FF2D55"),
    CategoryColorChoice("#FF3B30"),
    CategoryColorChoice("#FF9500"),
    CategoryColorChoice("#FFCC00"),
    CategoryColorChoice("#34C759"),
    CategoryColorChoice("#5AC8FA"),
    CategoryColorChoice("#5856D6"),
    CategoryColorChoice("#8E8E93")
)

private fun parseHexColor(hex: String?): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex ?: "#546E7A"))
    } catch (_: Exception) {
        Color(0xFF546E7A)
    }
}
