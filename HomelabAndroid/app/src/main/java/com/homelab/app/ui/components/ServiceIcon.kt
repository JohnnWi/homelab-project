package com.homelab.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.homelab.app.ui.theme.backgroundColor
import com.homelab.app.ui.theme.fallbackIcon
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
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = type.backgroundColor,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (content != null) {
                content()
            } else {
                SubcomposeAsyncImage(
                    model = type.iconUrl.takeIf { it.isNotBlank() },
                    contentDescription = type.displayName,
                    modifier = Modifier.size(iconSize),
                    contentScale = ContentScale.Fit,
                    error = {
                        Icon(
                            imageVector = type.fallbackIcon,
                            contentDescription = type.displayName,
                            tint = type.primaryColor,
                            modifier = Modifier.size(iconSize * 0.72f)
                        )
                    }
                )
            }
        }
    }
}
