package com.kuunyi.scanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NavPill(light: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .height(26.dp)
    ) {
        Box(
            modifier = Modifier
                .width(134.dp)
                .height(5.dp)
                .background(
                    color = if (light) Color.White.copy(alpha = 0.7f)
                            else Color.Black.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(999.dp),
                )
        )
    }
}
