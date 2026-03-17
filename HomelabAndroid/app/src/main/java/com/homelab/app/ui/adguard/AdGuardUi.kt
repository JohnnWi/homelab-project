package com.homelab.app.ui.adguard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homelab.app.R

@Composable
fun AdGuardGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
fun AdGuardSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun AdGuardEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AdGuardDeleteBackground(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isActive) MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                else MaterialTheme.colorScheme.background
            )
            .padding(end = 18.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (isActive) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.onError.copy(alpha = 0.12f)
            ) {
                androidx.compose.material3.Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(20.dp).padding(10.dp)
                )
            }
        }
    }
}
