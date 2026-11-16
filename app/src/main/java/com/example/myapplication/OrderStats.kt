package com.example.myapplication

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OrderStatsSummary(
    accepted: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$accepted",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = successText(),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = "—",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = "$total",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
