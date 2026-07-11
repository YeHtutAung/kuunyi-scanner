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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import com.kuunyi.scanner.ui.theme.Red
import com.kuunyi.scanner.ui.theme.InterTightFamily
import com.kuunyi.scanner.viewmodel.ScannerViewModel

@Composable
fun SettingsScreen(vm: ScannerViewModel) {
    val selectedEvent by vm.selectedEvent.collectAsStateWithLifecycle()
    val scanMode by vm.scanMode.collectAsStateWithLifecycle()
    val soundEnabled by vm.soundEnabled.collectAsStateWithLifecycle()
    val vibrateEnabled by vm.vibrateEnabled.collectAsStateWithLifecycle()
    val scanCount by vm.scanCount.collectAsStateWithLifecycle()
    val gateName by vm.gateName.collectAsStateWithLifecycle()
    val apiHost by vm.apiHost.collectAsStateWithLifecycle()
    val apiPort by vm.apiPort.collectAsStateWithLifecycle()

    var pinUnlocked by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
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

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
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

            SectionLabel("GATE")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                var hasTouched by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = gateName,
                    onValueChange = { vm.setGateName(it); hasTouched = true },
                    label = { Text("Gate name", fontSize = 13.sp) },
                    placeholder = { Text("e.g. Gate A, VIP Entrance", fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                    isError = hasTouched && gateName.isBlank(),
                )
                if (hasTouched && gateName.isBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Required — scanning is disabled without a gate name",
                        fontSize = 11.sp,
                        color = Red,
                    )
                }
            }
            HorizontalDivider(color = GrayF0)

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

            SectionLabel("SERVER")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Box {
                    OutlinedTextField(
                        value = apiHost,
                        onValueChange = { if (pinUnlocked) vm.setApiHost(it) },
                        label = { Text("API Host", fontSize = 13.sp) },
                        placeholder = { Text("https://api.example.com", fontSize = 13.sp) },
                        singleLine = true,
                        enabled = pinUnlocked,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            disabledTextColor = Dark,
                            disabledLabelColor = GrayAaa,
                        ),
                    )
                    if (!pinUnlocked) {
                        Box(modifier = Modifier
                            .matchParentSize()
                            .semantics { contentDescription = "Edit API host" }
                            .clickable { showPinDialog = true })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = apiPort,
                        onValueChange = { if (pinUnlocked) vm.setApiPort(it) },
                        label = { Text("Port", fontSize = 13.sp) },
                        placeholder = { Text("443", fontSize = 13.sp) },
                        singleLine = true,
                        enabled = pinUnlocked,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            disabledTextColor = Dark,
                            disabledLabelColor = GrayAaa,
                        ),
                    )
                    if (!pinUnlocked) {
                        Box(modifier = Modifier
                            .matchParentSize()
                            .semantics { contentDescription = "Edit port" }
                            .clickable { showPinDialog = true })
                    }
                }
            }
            HorizontalDivider(color = GrayF0)
            SettingsRow(
                label = "Reset to default",
                right = {
                    Text(
                        "Reset",
                        fontSize = 12.5.sp,
                        color = Red,
                        modifier = Modifier.clickable {
                            if (pinUnlocked) vm.resetApiConfig() else showPinDialog = true
                        }
                    )
                },
                showTopDivider = false,
            )

            Spacer(Modifier.height(8.dp))
        }

        NavPill(light = false)
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false; pinInput = ""; pinError = false },
            title = {
                Text(
                    "Enter PIN",
                    fontFamily = InterTightFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = Dark,
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) { pinInput = it; pinError = false } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (pinInput == vm.settingsPin) {
                                pinUnlocked = true; showPinDialog = false; pinInput = ""
                            } else {
                                pinError = true
                            }
                        }),
                        isError = pinError,
                        singleLine = true,
                        label = { Text("PIN", fontSize = 13.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "PIN input" },
                    )
                    if (pinError) {
                        Spacer(Modifier.height(4.dp))
                        Text("Incorrect PIN", fontSize = 11.sp, color = Red)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput == vm.settingsPin) {
                        pinUnlocked = true; showPinDialog = false; pinInput = ""
                    } else {
                        pinError = true
                    }
                }) {
                    Text(
                        "Confirm",
                        color = Dark,
                        fontFamily = InterTightFamily,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false; pinInput = ""; pinError = false }) {
                    Text("Cancel", color = GrayAaa, fontFamily = InterTightFamily)
                }
            },
            containerColor = Color.White,
        )
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
