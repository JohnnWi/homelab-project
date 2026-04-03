package com.homelab.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.homelab.app.ui.theme.backgroundColor
import com.homelab.app.ui.theme.fallbackIcon
import com.homelab.app.ui.theme.iconCandidates
import com.homelab.app.ui.theme.iconUrl
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType

@Composable
fun ServiceIcon(
    type: ServiceType,
    size: Dp = 56.dp,
    iconSize: Dp = size * 0.65f,
    cornerRadius: Dp = 14.dp,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null
) {
    val candidatesKey = remember(type) { type.iconCandidates.joinToString(separator = "|") }
    val iconSources = remember(type, candidatesKey) { type.iconCandidates.ifEmpty { listOf(type.iconUrl).filter { it.isNotBlank() } } }
    var sourceIndex by remember(type, candidatesKey) { mutableIntStateOf(0) }
    val currentSource = iconSources.getOrNull(sourceIndex)

    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = type.backgroundColor,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (content != null) {
                content()
            } else {
                val fallback: @Composable () -> Unit = {
                    Icon(
                        imageVector = type.fallbackIcon,
                        contentDescription = type.displayName,
                        tint = type.primaryColor,
                        modifier = Modifier.size(iconSize * 0.72f)
                    )
                }

                if (currentSource != null) {
                    SubcomposeAsyncImage(
                        model = currentSource,
                        contentDescription = type.displayName,
                        modifier = Modifier.size(iconSize),
                        contentScale = ContentScale.Fit,
                        loading = { fallback() },
                        error = {
                            if (sourceIndex < iconSources.lastIndex) {
                                LaunchedEffect(sourceIndex) {
                                    sourceIndex += 1
                                }
                            }
                            fallback()
                        }
                    )
                } else {
                    fallback()
                }
            }
        }
    }
}
