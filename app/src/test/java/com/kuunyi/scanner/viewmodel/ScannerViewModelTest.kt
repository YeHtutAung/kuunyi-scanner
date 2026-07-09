package com.kuunyi.scanner.viewmodel

import com.kuunyi.scanner.data.*
import com.kuunyi.scanner.network.ScanApiClient
import com.kuunyi.scanner.network.ScanApiResult
import com.kuunyi.scanner.util.TicketVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

// ---------- Fakes ----------

class FakeVerifier(
    private val result: TicketPayload? = null,
    private val throws: Exception? = null,
) : TicketVerifier(publicKeys = emptyMap()) {
    override fun verify(raw: String, expectedEid: String): TicketPayload {
        throws?.let { throw it }
        return result ?: TicketPayload("TKT-001", "evt-summer-2026", "VIP", 1, Long.MAX_VALUE)
    }
}

class FakeApiClient(
    private val result: ScanApiResult = ScanApiResult.Ok,
) : ScanApiClient(baseUrl = "", apiKey = "", versionCode = 0) {
    override suspend fun recordScan(jti: String, eid: String, gate: String) = result
}

// ---------- Tests ----------

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Helper: ViewModel with fakes, gate name pre-set so scans are not blocked. */
    private fun vm(
        verifier: TicketVerifier = FakeVerifier(),
        api: ScanApiClient = FakeApiClient(),
    ) = ScannerViewModel(verifier = verifier, apiClient = api).also { it.setGateName("Gate A") }

    // --- Initial state ---

    @Test fun `initial screen is EventPicker`() {
        assertEquals(Screen.EventPicker, ScannerViewModel(verifier = FakeVerifier(), apiClient = FakeApiClient()).screen.value)
    }

    @Test fun `initial scan count is 0`() {
        assertEquals(0, ScannerViewModel(verifier = FakeVerifier(), apiClient = FakeApiClient()).scanCount.value)
    }

    @Test fun `initial selected event is the first`() {
        val v = ScannerViewModel(verifier = FakeVerifier(), apiClient = FakeApiClient())
        assertEquals("evt-summer-2026", v.selectedEvent.value.id)
    }

    // --- Navigation ---

    @Test fun `startScanning navigates to Scanner`() {
        val v = vm(); v.startScanning()
        assertEquals(Screen.Scanner, v.screen.value)
    }

    @Test fun `openSettings navigates to Settings`() {
        val v = vm(); v.openSettings()
        assertEquals(Screen.Settings, v.screen.value)
    }

    @Test fun `closeSettings returns to Scanner`() {
        val v = vm(); v.openSettings(); v.closeSettings()
        assertEquals(Screen.Scanner, v.screen.value)
    }

    @Test fun `switchEvent navigates to EventPicker`() {
        val v = vm(); v.switchEvent()
        assertEquals(Screen.EventPicker, v.screen.value)
    }

    @Test fun `selectEvent updates selected event`() {
        val v = vm()
        v.selectEvent(v.events[1])
        assertEquals("evt-night-aug", v.selectedEvent.value.id)
    }

    @Test fun `onNext returns to Scanner`() {
        val v = vm(); v.onDemoResult(ScanResult.Valid("T", "GA", 1)); v.onNext()
        assertEquals(Screen.Scanner, v.screen.value)
    }

    // --- Scan results ---

    @Test fun `valid scan navigates to Result and increments count`() = runTest {
        val v = vm(api = FakeApiClient(ScanApiResult.Ok))
        v.onBarcodeDetected("any")
        assertEquals(Screen.Result, v.screen.value)
        assertTrue(v.currentResult.value is ScanResult.Valid)
        assertEquals(1, v.scanCount.value)
    }

    @Test fun `already-used scan shows AlreadyUsed and does not increment count`() = runTest {
        val v = vm(api = FakeApiClient(ScanApiResult.AlreadyUsed("Today · 9 AM", "Gate B")))
        v.onBarcodeDetected("any")
        assertTrue(v.currentResult.value is ScanResult.AlreadyUsed)
        assertEquals(0, v.scanCount.value)
    }

    @Test fun `server 404 shows FakeTicket`() = runTest {
        val v = vm(api = FakeApiClient(ScanApiResult.NotFound))
        v.onBarcodeDetected("any")
        assertTrue(v.currentResult.value is ScanResult.FakeTicket)
    }

    @Test fun `invalid signature shows FakeTicket`() = runTest {
        val v = vm(verifier = FakeVerifier(throws = InvalidSignatureException()))
        v.onBarcodeDetected("any")
        assertTrue(v.currentResult.value is ScanResult.FakeTicket)
    }

    @Test fun `malformed token shows FakeTicket`() = runTest {
        val v = vm(verifier = FakeVerifier(throws = MalformedTokenException()))
        v.onBarcodeDetected("any")
        assertTrue(v.currentResult.value is ScanResult.FakeTicket)
    }

    @Test fun `unknown key shows FakeTicket`() = runTest {
        val v = vm(verifier = FakeVerifier(throws = UnknownKeyException("v99")))
        v.onBarcodeDetected("any")
        assertTrue(v.currentResult.value is ScanResult.FakeTicket)
    }

    @Test fun `expired JWT shows Expired result with correct jti`() = runTest {
        val v = vm(verifier = FakeVerifier(throws = ExpiredException("TKT-OLD", "GA", 1_000L)))
        v.onBarcodeDetected("any")
        val r = v.currentResult.value as ScanResult.Expired
        assertEquals("TKT-OLD", r.ticketId)
        assertEquals("GA", r.tier)
    }

    @Test fun `wrong event shows WrongEntrance with correct tier`() = runTest {
        val v = vm(verifier = FakeVerifier(throws = WrongEventException("VIP", "evt-other", "evt-summer-2026")))
        v.onBarcodeDetected("any")
        val r = v.currentResult.value as ScanResult.WrongEntrance
        assertEquals("VIP", r.ticketTier)
    }

    // --- Error toasts ---

    @Test fun `blank gate name shows toast and does not navigate`() = runTest {
        val v = ScannerViewModel(verifier = FakeVerifier(), apiClient = FakeApiClient())
        v.onBarcodeDetected("any")
        assertNotNull(v.toastMessage.value)
        assertNull(v.currentResult.value)
    }

    @Test fun `network error shows toast and increments scanResetKey`() = runTest {
        val v = vm(api = FakeApiClient(ScanApiResult.NetworkError))
        val keyBefore = v.scanResetKey.value
        v.onBarcodeDetected("any")
        assertNotNull(v.toastMessage.value)
        assertEquals(keyBefore + 1, v.scanResetKey.value)
        assertNull(v.currentResult.value)
    }

    @Test fun `auth error shows toast and increments scanResetKey`() = runTest {
        val v = vm(api = FakeApiClient(ScanApiResult.AuthError))
        val keyBefore = v.scanResetKey.value
        v.onBarcodeDetected("any")
        assertNotNull(v.toastMessage.value)
        assertEquals(keyBefore + 1, v.scanResetKey.value)
    }

    // --- Settings ---

    @Test fun `setSoundEnabled updates soundEnabled`() {
        val v = vm(); v.setSoundEnabled(false); assertFalse(v.soundEnabled.value)
    }

    @Test fun `setVibrateEnabled updates vibrateEnabled`() {
        val v = vm(); v.setVibrateEnabled(false); assertFalse(v.vibrateEnabled.value)
    }

    @Test fun `setScanMode updates scanMode`() {
        val v = vm(); v.setScanMode(ScanMode.TAP); assertEquals(ScanMode.TAP, v.scanMode.value)
    }

    @Test fun `clearToast nullifies toastMessage`() = runTest {
        val v = vm(api = FakeApiClient(ScanApiResult.NetworkError))
        v.onBarcodeDetected("any")
        v.clearToast()
        assertNull(v.toastMessage.value)
    }

    @Test fun `demo result valid increments count`() {
        val v = vm()
        v.onDemoResult(ScanResult.Valid("T", "VIP", 1))
        assertEquals(1, v.scanCount.value)
    }

    @Test fun `demo result non-valid does not increment count`() {
        val v = vm()
        v.onDemoResult(ScanResult.FakeTicket())
        assertEquals(0, v.scanCount.value)
    }
}
