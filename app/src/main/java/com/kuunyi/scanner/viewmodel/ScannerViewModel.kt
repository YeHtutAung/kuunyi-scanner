package com.kuunyi.scanner.viewmodel

import androidx.lifecycle.ViewModel
import com.kuunyi.scanner.data.Event
import com.kuunyi.scanner.data.ScanMode
import com.kuunyi.scanner.data.ScanResult
import com.kuunyi.scanner.data.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScannerViewModel : ViewModel() {

    val events = listOf(
        Event("1", "Summer Fest 2026", "Jul 12", "Main Arena"),
        Event("2", "Night Market Aug", "Aug 03", "Riverside"),
    )

    private val _screen = MutableStateFlow<Screen>(Screen.EventPicker)
    val screen = _screen.asStateFlow()

    private val _selectedEvent = MutableStateFlow(events.first())
    val selectedEvent = _selectedEvent.asStateFlow()

    private val _scanMode = MutableStateFlow(ScanMode.CONTINUOUS)
    val scanMode = _scanMode.asStateFlow()

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled = _soundEnabled.asStateFlow()

    private val _vibrateEnabled = MutableStateFlow(true)
    val vibrateEnabled = _vibrateEnabled.asStateFlow()

    private val _scanCount = MutableStateFlow(1204)
    val scanCount = _scanCount.asStateFlow()

    private val _currentResult = MutableStateFlow<ScanResult?>(null)
    val currentResult = _currentResult.asStateFlow()

    // Demo results cycle for testing without real ticket QRs
    private val demoResults = listOf(
        ScanResult.Valid("#TKT-9F2A31", "VIP", 1),
        ScanResult.AlreadyUsed("#TKT-4B77C0", "GA", "Today · 8:52 AM", "Gate — Main Arena"),
        ScanResult.FakeTicket("#TKT-FAKE01"),
        ScanResult.Expired("#TKT-2210AA", "GA", "Jul 11 · 6:00–10:00 PM", "Jul 12 · 9:41 AM"),
        ScanResult.WrongEntrance("#TKT-77C931", "GA", "VIP"),
    )
    private var demoIndex = 0

    fun selectEvent(event: Event) {
        _selectedEvent.value = event
    }

    fun startScanning() {
        _screen.value = Screen.Scanner
    }

    fun onBarcodeDetected(rawValue: String) {
        // In production: decrypt and validate the signed QR payload against the server.
        // For now: cycle through demo result states.
        val result = parseBarcode(rawValue)
        handleResult(result)
    }

    fun onDemoResult(result: ScanResult) {
        handleResult(result)
    }

    private fun handleResult(result: ScanResult) {
        _currentResult.value = result
        if (result is ScanResult.Valid) _scanCount.value++
        _screen.value = Screen.Result
    }

    private fun parseBarcode(raw: String): ScanResult {
        val result = demoResults[demoIndex % demoResults.size]
        demoIndex++
        return result
    }

    fun onNext() {
        _screen.value = Screen.Scanner
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun closeSettings() {
        _screen.value = Screen.Scanner
    }

    fun switchEvent() {
        _screen.value = Screen.EventPicker
    }

    fun setScanMode(mode: ScanMode) { _scanMode.value = mode }
    fun setSoundEnabled(v: Boolean) { _soundEnabled.value = v }
    fun setVibrateEnabled(v: Boolean) { _vibrateEnabled.value = v }
}
