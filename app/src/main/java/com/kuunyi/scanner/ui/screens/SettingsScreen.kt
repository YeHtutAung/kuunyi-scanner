package com.kuunyi.scanner.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuunyi.scanner.data.ScanMode
import com.kuunyi.scanner.ui.theme.Blue
import com.kuunyi.scanner.ui.theme.Dark
import com.kuunyi.scanner.ui.theme.Green
import com.kuunyi.scanner.ui.theme.GrayAaa
import com.kuunyi.scanner.ui.theme.GrayF0
import com.kuunyi.scanner.ui.theme.InterTightFamily
import com.kuunyi.scanner.viewmodel.ScannerViewModel

@Composable
fun SettingsScreen(vm: ScannerViewModel) {
    val selectedEvent by vm.selectedEvent.collectAsStateWithLifecycle()
    val scanMode by vm.scanMode.collectAsStateWithLifecycle()
    val soundEnabled by vm.soundEnabled.collectAsStateWithLifecycle()
    val vibrateEnabled by vm.vibrateEnabled.collectAsStateWithLifecycle()
    val scanCount by vm.scanCount.collectAsStateWithLifecycle()

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
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .padding(bottom = 10.dp)
        ) {
            IconButton(onClick = vm::closeSettings) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Dark,
                )
            }
            Text(
                text = "Settings",
                fontFamily = InterTightFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = Dark,
            )
        }
        HorizontalDivider(color = GrayF0)

        Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
            SectionLabel("SCANNING")

            SettingsRow(
                label = "Default mode",
                right = {
                    Text(
                        text = if (scanMode == ScanMode.CONTINUOUS) "Continuous \u25be" else "Tap to scan \u25be",
                        fontSize = 12.5.sp,
                        color = Color(0xFF888888),
                        modifier = Modifier.clickable {
                            vm.setScanMode(
                                if (scanMode == ScanMode.CONTINUOUS) ScanMode.TAP else ScanMode.CONTINUOUS
                            )
                        }
                    )
                },
                showTopDivider = false,
            )
            SettingsRow(
                label = "Sound on scan",
                right = { Toggle(value = soundEnabled, onChange = vm::setSoundEnabled) },
            )
            SettingsRow(
                label = "Vibrate on result",
                right = { Toggle(value = vibrateEnabled, onChange = vm::setVibrateEnabled) },
            )

            SectionLabel("EVENT")

            SettingsRow(
                label = selectedEvent.name,
                right = {
                    Text(
                        "Switch",
                        fontSize = 12.5.sp,
                        color = Blue,
                        modifier = Modifier.clickable { vm.switchEvent() }
                    )
                },
                showTopDivider = false,
            )
            SettingsRow(
                label = "Server sync",
                right = {
                    Text("\u25cf Online", fontSize = 12.5.sp, color = Green)
                },
            )
            SettingsRow(
                label = "Scanned today",
                right = {
                    Text(
                        "%,d".format(scanCount),
                        fontFamily = InterTightFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Dark,
                    )
                },
            )
        }

        NavPill(light = false)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        letterSpacing = 0.5.sp,
        color = GrayAaa,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    right: @Composable () -> Unit,
    showTopDivider: Boolean = true,
) {
    if (showTopDivider) HorizontalDivider(color = GrayF0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.5.sp, color = Dark)
        right()
    }
}

@Composable
private fun Toggle(value: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .background(
                if (value) Green else Color(0xFFD8D6D0),
                RoundedCornerShape(999.dp)
            )
            .clickable { onChange(!value) }
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .padding(2.dp)
                .align(if (value) Alignment.CenterEnd else Alignment.CenterStart)
                .background(Color.White, CircleShape)
        )
    }
}
