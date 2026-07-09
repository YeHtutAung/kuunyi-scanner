package com.kuunyi.scanner.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuunyi.scanner.data.Event
import com.kuunyi.scanner.ui.theme.Dark
import com.kuunyi.scanner.ui.theme.GrayAaa
import com.kuunyi.scanner.ui.theme.GrayD8
import com.kuunyi.scanner.ui.theme.InterTightFamily
import com.kuunyi.scanner.viewmodel.ScannerViewModel

@Composable
fun EventPickerScreen(vm: ScannerViewModel) {
    val selectedEvent by vm.selectedEvent.collectAsStateWithLifecycle()

    // Light status bar (dark icons on white background)
    val view = LocalView.current
    val window = (LocalContext.current as Activity).window
    SideEffect {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 26.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(34.dp))

            // Logo placeholder
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .border(
                        width = 2.dp,
                        color = Color(0xFFBBBBBB),
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Text(
                    text = "logo",
                    fontSize = 11.sp,
                    color = Color(0xFF999999),
                    fontStyle = FontStyle.Italic,
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Ticket\nVerify",
                fontFamily = InterTightFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                color = Dark,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Choose the event you\u2019re scanning",
                fontSize = 12.5.sp,
                color = Color(0xFF888888),
            )

            Spacer(Modifier.height(26.dp))

            vm.events.forEach { event ->
                EventItem(
                    event = event,
                    isSelected = selectedEvent.id == event.id,
                    onSelect = { vm.selectEvent(event) },
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(32.dp))
        }

        // Start scanning button
        Box(
            modifier = Modifier
                .padding(horizontal = 26.dp)
                .padding(bottom = 20.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Dark, RoundedCornerShape(13.dp))
                    .clickable { vm.startScanning() }
                    .padding(vertical = 15.dp)
            ) {
                Text(
                    text = "Start scanning",
                    fontFamily = InterTightFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White,
                )
            }
        }

        // Nav bar pill
        NavPill(light = false)
    }
}

@Composable
private fun EventItem(event: Event, isSelected: Boolean, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isSelected) 1f else 0.7f)
            .border(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = if (isSelected) Dark else GrayD8,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onSelect() }
            .padding(horizontal = 15.dp, vertical = 14.dp)
    ) {
        Text(
            text = event.name,
            fontFamily = InterTightFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (isSelected) Dark else Color(0xFF666666),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "${event.date} \u00b7 ${event.location}${if (isSelected) " \u00b7 selected \u2713" else ""}",
            fontSize = 11.5.sp,
            color = if (isSelected) GrayAaa else Color(0xFFAAAAAA),
        )
    }
}
