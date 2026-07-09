package com.kuunyi.scanner.viewmodel

import com.kuunyi.scanner.data.ScanMode
import com.kuunyi.scanner.data.ScanResult
import com.kuunyi.scanner.data.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScannerViewModelTest {

    private lateinit var vm: ScannerViewModel

    @Before
    fun setUp() {
        vm = ScannerViewModel()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial screen is EventPicker`() {
        assertEquals(Screen.EventPicker, vm.screen.value)
    }

    @Test
    fun `initial selected event is the first in the list`() {
        assertEquals(vm.events.first(), vm.selectedEvent.value)
    }

    @Test
    fun `initial scan mode is CONTINUOUS`() {
        assertEquals(ScanMode.CONTINUOUS, vm.scanMode.value)
    }

    @Test
    fun `initial sound enabled is true`() {
        assertTrue(vm.soundEnabled.value)
    }

    @Test
    fun `initial vibrate enabled is true`() {
        assertTrue(vm.vibrateEnabled.value)
    }

    @Test
    fun `initial current result is null`() {
        assertEquals(null, vm.currentResult.value)
    }

    @Test
    fun `events list is not empty`() {
        assertTrue(vm.events.isNotEmpty())
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    @Test
    fun `startScanning navigates to Scanner`() {
        vm.startScanning()
        assertEquals(Screen.Scanner, vm.screen.value)
    }

    @Test
    fun `openSettings navigates to Settings`() {
        vm.openSettings()
        assertEquals(Screen.Settings, vm.screen.value)
    }

    @Test
    fun `closeSettings navigates back to Scanner`() {
        vm.openSettings()
        vm.closeSettings()
        assertEquals(Screen.Scanner, vm.screen.value)
    }

    @Test
    fun `switchEvent navigates to EventPicker`() {
        vm.startScanning()
        vm.switchEvent()
        assertEquals(Screen.EventPicker, vm.screen.value)
    }

    @Test
    fun `onNext navigates to Scanner`() {
        vm.onDemoResult(ScanResult.Valid("#TKT-01", "VIP", 1))
        vm.onNext()
        assertEquals(Screen.Scanner, vm.screen.value)
    }

    // ── Settings toggles ───────────────────────────────────────────────────────

    @Test
    fun `selectEvent updates selectedEvent`() {
        val second = vm.events[1]
        vm.selectEvent(second)
        assertEquals(second, vm.selectedEvent.value)
    }

    @Test
    fun `setScanMode TAP updates scanMode`() {
        vm.setScanMode(ScanMode.TAP)
        assertEquals(ScanMode.TAP, vm.scanMode.value)
    }

    @Test
    fun `setScanMode toggles back to CONTINUOUS`() {
        vm.setScanMode(ScanMode.TAP)
        vm.setScanMode(ScanMode.CONTINUOUS)
        assertEquals(ScanMode.CONTINUOUS, vm.scanMode.value)
    }

    @Test
    fun `setSoundEnabled false updates soundEnabled`() {
        vm.setSoundEnabled(false)
        assertFalse(vm.soundEnabled.value)
    }

    @Test
    fun `setSoundEnabled true restores soundEnabled`() {
        vm.setSoundEnabled(false)
        vm.setSoundEnabled(true)
        assertTrue(vm.soundEnabled.value)
    }

    @Test
    fun `setVibrateEnabled false updates vibrateEnabled`() {
        vm.setVibrateEnabled(false)
        assertFalse(vm.vibrateEnabled.value)
    }

    // ── Scan results ───────────────────────────────────────────────────────────

    @Test
    fun `onDemoResult sets currentResult`() {
        val result = ScanResult.Valid("#TKT-01", "VIP", 1)
        vm.onDemoResult(result)
        assertEquals(result, vm.currentResult.value)
    }

    @Test
    fun `onDemoResult navigates to Result screen`() {
        vm.onDemoResult(ScanResult.FakeTicket("#TKT-FAKE"))
        assertEquals(Screen.Result, vm.screen.value)
    }

    @Test
    fun `Valid result increments scan count by 1`() {
        val before = vm.scanCount.value
        vm.onDemoResult(ScanResult.Valid("#TKT-01", "GA", 1))
        assertEquals(before + 1, vm.scanCount.value)
    }

    @Test
    fun `AlreadyUsed result does not increment scan count`() {
        val before = vm.scanCount.value
        vm.onDemoResult(ScanResult.AlreadyUsed("#TKT-01", "GA", "8:52 AM", "Gate A"))
        assertEquals(before, vm.scanCount.value)
    }

    @Test
    fun `FakeTicket result does not increment scan count`() {
        val before = vm.scanCount.value
        vm.onDemoResult(ScanResult.FakeTicket("#TKT-FAKE"))
        assertEquals(before, vm.scanCount.value)
    }

    @Test
    fun `Expired result does not increment scan count`() {
        val before = vm.scanCount.value
        vm.onDemoResult(ScanResult.Expired("#TKT-01", "GA", "Jul 11", "Jul 12"))
        assertEquals(before, vm.scanCount.value)
    }

    @Test
    fun `WrongEntrance result does not increment scan count`() {
        val before = vm.scanCount.value
        vm.onDemoResult(ScanResult.WrongEntrance("#TKT-01", "GA", "VIP"))
        assertEquals(before, vm.scanCount.value)
    }

    @Test
    fun `multiple valid scans accumulate in scan count`() {
        val before = vm.scanCount.value
        repeat(5) { i -> vm.onDemoResult(ScanResult.Valid("#TKT-0$i", "VIP", 1)) }
        assertEquals(before + 5, vm.scanCount.value)
    }

    @Test
    fun `only valid results counted across mixed scans`() {
        val before = vm.scanCount.value
        vm.onDemoResult(ScanResult.Valid("#T1", "VIP", 1))
        vm.onDemoResult(ScanResult.AlreadyUsed("#T2", "GA", "8:52 AM", "Gate A"))
        vm.onDemoResult(ScanResult.FakeTicket("#T3"))
        vm.onDemoResult(ScanResult.Valid("#T4", "GA", 1))
        vm.onDemoResult(ScanResult.Expired("#T5", "GA", "window", "scanned"))
        assertEquals(before + 2, vm.scanCount.value)
    }

    // ── onBarcodeDetected (demo cycling) ───────────────────────────────────────

    @Test
    fun `onBarcodeDetected sets a result`() {
        vm.onBarcodeDetected("some-qr-data")
        assertNotNull(vm.currentResult.value)
    }

    @Test
    fun `onBarcodeDetected navigates to Result screen`() {
        vm.onBarcodeDetected("some-qr-data")
        assertEquals(Screen.Result, vm.screen.value)
    }

    @Test
    fun `onBarcodeDetected cycles through different result types`() {
        val types = (1..5).map { i ->
            vm.onBarcodeDetected("qr-$i")
            vm.currentResult.value!!::class
        }.distinct()
        assertTrue("Expected multiple result types across 5 scans", types.size > 1)
    }
}
