package com.example.myapplication

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OrderStatsRing(
    accepted: Int,
    rejected: Int,
    rework: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val acceptedColor = successText()
    val rejectedColor = Color(0xFFF44336)
    val reworkColor = warnText()
    val uncheckedColor = MaterialTheme.colorScheme.surfaceVariant
    val denominator = maxOf(total, accepted + rejected + rework)
    val acceptedPercent = if (total > 0) (accepted * 100 / total).coerceIn(0, 100) else 0

    Box(modifier = modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 4.dp.toPx())
            val topLeft = Offset(stroke.width / 2, stroke.width / 2)
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)

            drawArc(
                color = uncheckedColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            var startAngle = -90f
            fun drawSegment(count: Int, color: Color) {
                if (count <= 0 || denominator <= 0) return
                val sweepAngle = 360f * count / denominator
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
                startAngle += sweepAngle
            }

            drawSegment(accepted, acceptedColor)
            drawSegment(rejected, rejectedColor)
            drawSegment(rework, reworkColor)
        }

        Text(
            text = if (total > 0) "$acceptedPercent%" else "—",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}
