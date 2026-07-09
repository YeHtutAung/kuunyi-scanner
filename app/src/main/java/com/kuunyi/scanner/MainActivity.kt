package com.kuunyi.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuunyi.scanner.data.Screen
import com.kuunyi.scanner.ui.screens.EventPickerScreen
import com.kuunyi.scanner.ui.screens.ResultScreen
import com.kuunyi.scanner.ui.screens.ScannerScreen
import com.kuunyi.scanner.ui.screens.SettingsScreen
import com.kuunyi.scanner.ui.theme.KuunyiScannerTheme
import com.kuunyi.scanner.viewmodel.ScannerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KuunyiScannerTheme {
                val vm: ScannerViewModel = viewModel()
                val screen by vm.screen.collectAsStateWithLifecycle()

                when (screen) {
                    Screen.EventPicker -> EventPickerScreen(vm)
                    Screen.Scanner    -> ScannerScreen(vm)
                    Screen.Result     -> ResultScreen(vm)
                    Screen.Settings   -> SettingsScreen(vm)
                }
            }
        }
    }
}
