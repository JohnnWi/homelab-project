package com.homelab.app.ui.beszel

import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun SmoothLineGraph(
    data: List<Double>,
    graphColor: Color,
    secondaryData: List<Double>? = null,
    secondaryColor: Color = Color.Red,
    enableScrub: Boolean = false,
    selectedIndex: Int? = null,
    onSelectedIndexChange: ((Int?) -> Unit)? = null,
    labelFormatter: ((Double) -> String)? = null,
    secondaryLabelFormatter: ((Double) -> String)? = null,
    gridlines: List<Double>? = null,
    minValueOverride: Double? = null,
    maxValueOverride: Double? = null,
    heightDp: androidx.compose.ui.unit.Dp = 48.dp
) {
    if (data.size < 2) return

    val primaryMax = data.maxOrNull() ?: 1.0
    val primaryMin = data.minOrNull() ?: 0.0
    val combinedMax = secondaryData?.maxOrNull()?.let { maxOf(primaryMax, it) } ?: primaryMax
    val combinedMin = secondaryData?.minOrNull()?.let { minOf(primaryMin, it) } ?: primaryMin

    val maxVal = (maxValueOverride ?: combinedMax).coerceAtLeast(1.0)
    val minVal = (minValueOverride ?: combinedMin).coerceAtMost(maxVal - 0.1)
    val range = (maxVal - minVal).coerceAtLeast(0.1)

    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "graph_appear"
    )

    val haptic = LocalHapticFeedback.current
    var dragX by remember(data.size) { mutableStateOf(0f) }
    var graphWidthPx by remember(data.size) { mutableStateOf(1f) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
                .onSizeChanged { graphWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(enableScrub, data.size, selectedIndex) {
                    if (!enableScrub || onSelectedIndexChange == null || data.isEmpty()) return@pointerInput
                    fun updateSelection(x: Float, lastIndex: Int?): Int {
                        val widthPx = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = (x / widthPx).coerceIn(0f, 1f)
                        val idx = ((fraction * (data.size - 1)).roundToInt()).coerceIn(0, data.size - 1)
                        onSelectedIndexChange(idx)
                        if (idx != lastIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        return idx
                    }

                    detectTapGestures(
                        onTap = { offset ->
                            dragX = offset.x
                            updateSelection(offset.x, selectedIndex)
                        }
                    )
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enableScrub && onSelectedIndexChange != null && data.isNotEmpty(),
                    state = rememberDraggableState { delta ->
                        val widthPx = graphWidthPx.coerceAtLeast(1f)
                        val nextX = (dragX + delta).coerceIn(0f, widthPx)
                        dragX = nextX
                        val fraction = (nextX / widthPx).coerceIn(0f, 1f)
                        val idx = ((fraction * (data.size - 1)).roundToInt()).coerceIn(0, data.size - 1)
                        onSelectedIndexChange?.invoke(idx)
                        if (idx != selectedIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragStarted = { offset ->
                        val widthPx = graphWidthPx.coerceAtLeast(1f)
                        dragX = offset.x.coerceIn(0f, widthPx)
                        val fraction = (dragX / widthPx).coerceIn(0f, 1f)
                        val idx = ((fraction * (data.size - 1)).roundToInt()).coerceIn(0, data.size - 1)
                        onSelectedIndexChange?.invoke(idx)
                        if (idx != selectedIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                )
        ) {
            val width = size.width
            val height = size.height
            val stepX = width / (data.size - 1).coerceAtLeast(1)
            gridlines?.forEach { value ->
                val clamped = ((value - minVal) / range).coerceIn(0.0, 1.0)
                val y = height - (clamped * height).toFloat()
                drawLine(
                    if (value == 0.0) gridColor.copy(alpha = 0.7f) else gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (value == 0.0) 1.6.dp.toPx() else 1.dp.toPx()
                )
            }

            val path = Path()
            val fillPath = Path()
            val secondaryPath = if (secondaryData != null && secondaryData.size >= 2) Path() else null

            var previousX = 0f
            var previousY = height - ((data.first() - minVal) / range * height).toFloat() * animationProgress

            path.moveTo(previousX, previousY)
            fillPath.moveTo(0f, height)
            fillPath.lineTo(0f, previousY)

            for (i in 1 until data.size) {
                val x = i * stepX
                val y = height - ((data[i] - minVal) / range * height).toFloat() * animationProgress

                val controlX1 = previousX + (x - previousX) / 2f
                val controlY1 = previousY
                val controlX2 = previousX + (x - previousX) / 2f
                val controlY2 = y

                path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)

                previousX = x
                previousY = y
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // Secondary line path (no fill)
            secondaryPath?.let { secPath ->
                var secPrevX = 0f
                var secPrevY = height - ((secondaryData!![0] - minVal) / range * height).toFloat() * animationProgress
                secPath.moveTo(secPrevX, secPrevY)

                for (i in 1 until secondaryData.size) {
                    val x = i * stepX
                    val y = height - ((secondaryData[i] - minVal) / range * height).toFloat() * animationProgress

                    val c1x = secPrevX + (x - secPrevX) / 2f
                    val c1y = secPrevY
                    val c2x = secPrevX + (x - secPrevX) / 2f
                    val c2y = y

                    secPath.cubicTo(c1x, c1y, c2x, c2y, x, y)

                    secPrevX = x
                    secPrevY = y
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(graphColor.copy(alpha = 0.4f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            drawPath(
                path = path,
                color = graphColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw secondary line on top
            secondaryPath?.let { secPath ->
                drawPath(
                    path = secPath,
                    color = secondaryColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            if (enableScrub && selectedIndex != null && selectedIndex in data.indices) {
                val idx = selectedIndex.coerceIn(0, data.size - 1)
                val x = idx * stepX
                val y = height - ((data[idx] - minVal) / range * height).toFloat() * animationProgress

                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1.dp.toPx()
                )

                drawCircle(
                    color = graphColor,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )

                labelFormatter?.let { primaryFormatter ->
                    val primaryText = primaryFormatter(data[idx])
                    val secondaryValue = secondaryData?.getOrNull(idx)
                    val secondaryText = if (secondaryValue != null && secondaryLabelFormatter != null) {
                        secondaryLabelFormatter.invoke(secondaryValue)
                    } else {
                        null
                    }

                    val textSizePx = 14.sp.toPx()
                    val paddingX = 6.dp.toPx()
                    val paddingY = 4.dp.toPx()
                    val lineSpacing = 4.dp.toPx()
                    val dotSize = 6.dp.toPx()
                    val dotGap = 4.dp.toPx()

                    val paint = Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.WHITE
                        textSize = textSizePx
                    }

                    val primaryTextWidth = paint.measureText(primaryText)
                    val secondaryTextWidth = secondaryText?.let { paint.measureText(it) } ?: 0f
                    val textColumnWidth = maxOf(primaryTextWidth, secondaryTextWidth)
                    val hasSecondary = secondaryText != null
                    val boxWidth = paddingX * 2 + dotSize + dotGap + textColumnWidth
                    val boxHeight = paddingY * 2 + textSizePx * (if (hasSecondary) 2f else 1f) + if (hasSecondary) lineSpacing else 0f

                    val rawX = x - boxWidth / 2f
                    val rawY = y - 32.dp.toPx() - boxHeight
                    val boxLeft = rawX.coerceIn(0f, width - boxWidth)
                    val boxTop = rawY.coerceAtLeast(4.dp.toPx())

                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.7f),
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxWidth, boxHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )

                    val dotRadius = dotSize / 2f
                    val line1Y = boxTop + paddingY + textSizePx * 0.85f
                    val line2Y = line1Y + textSizePx + lineSpacing
                    val dotX = boxLeft + paddingX + dotRadius
                    val textX = boxLeft + paddingX + dotSize + dotGap

                    // Primary line
                    drawCircle(
                        color = graphColor,
                        radius = dotRadius,
                        center = Offset(dotX, line1Y - textSizePx * 0.5f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(primaryText, textX, line1Y, paint)

                    // Optional secondary line
                    if (hasSecondary) {
                        drawCircle(
                            color = secondaryColor,
                            radius = dotRadius,
                            center = Offset(dotX, line2Y - textSizePx * 0.5f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(secondaryText, textX, line2Y, paint)
                    }
                }
            }
        }
    }
}
