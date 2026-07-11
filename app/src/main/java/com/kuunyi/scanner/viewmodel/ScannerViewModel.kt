package com.kuunyi.scanner.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuunyi.scanner.BuildConfig
import com.kuunyi.scanner.data.*
import com.kuunyi.scanner.network.ScanApiClient
import com.kuunyi.scanner.network.ScanApiResult
import com.kuunyi.scanner.util.TicketVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ScannerViewModel(
    private val prefs: SharedPreferences? = null,
    private val verifier: TicketVerifier = TicketVerifier(
        publicKeys = mapOf("v1" to BuildConfig.ED25519_PUBLIC_KEY_V1)
    ),
    apiClient: ScanApiClient? = null,
) : ViewModel() {

    val settingsPin: String = BuildConfig.SETTINGS_PIN

    private val _apiHost: MutableStateFlow<String>
    private val _apiPort: MutableStateFlow<String>
    val apiHost: StateFlow<String>
    val apiPort: StateFlow<String>
    private val apiClient: ScanApiClient

    init {
        val host = prefs?.getString("api_host", BuildConfig.SCAN_API_HOST) ?: BuildConfig.SCAN_API_HOST
        val port = prefs?.getString("api_port", BuildConfig.SCAN_API_PORT) ?: BuildConfig.SCAN_API_PORT
        _apiHost = MutableStateFlow(host)
        _apiPort = MutableStateFlow(port)
        apiHost = _apiHost.asStateFlow()
        apiPort = _apiPort.asStateFlow()
        this.apiClient = apiClient ?: ScanApiClient(
            baseUrl = buildBaseUrl(host, port),
            apiKey = BuildConfig.SCAN_API_KEY,
            versionCode = BuildConfig.VERSION_CODE,
        )
    }

    val events = listOf(
        Event("evt-summer-2026", "Summer Fest 2026", "Jul 12", "Main Arena"),
        Event("evt-night-aug",   "Night Market Aug", "Aug 03", "Riverside"),
    )

    private val _screen        = MutableStateFlow<Screen>(Screen.EventPicker)
    private val _selectedEvent = MutableStateFlow(events.first())
    private val _scanMode      = MutableStateFlow(ScanMode.CONTINUOUS)
    private val _soundEnabled  = MutableStateFlow(true)
    private val _vibrateEnabled = MutableStateFlow(true)
    private val _scanCount     = MutableStateFlow(0)
    private val _currentResult = MutableStateFlow<ScanResult?>(null)
    private val _loading       = MutableStateFlow(false)
    private val _gateName      = MutableStateFlow("")
    private val _toastMessage  = MutableStateFlow<String?>(null)

    // Incrementing this signals CameraPreview to reset its detection state.
    // Incremented after non-navigate errors (network/auth/server) so the user can scan again.
    private val _scanResetKey  = MutableStateFlow(0)

    val screen         = _screen.asStateFlow()
    val selectedEvent  = _selectedEvent.asStateFlow()
    val scanMode       = _scanMode.asStateFlow()
    val soundEnabled   = _soundEnabled.asStateFlow()
    val vibrateEnabled = _vibrateEnabled.asStateFlow()
    val scanCount      = _scanCount.asStateFlow()
    val currentResult  = _currentResult.asStateFlow()
    val loading        = _loading.asStateFlow()
    val gateName       = _gateName.asStateFlow()
    val toastMessage   = _toastMessage.asStateFlow()
    val scanResetKey   = _scanResetKey.asStateFlow()

    fun setGateName(name: String) { _gateName.value = name }
    fun clearToast() { _toastMessage.value = null }

    fun setApiHost(host: String) {
        _apiHost.value = host
        prefs?.edit()?.putString("api_host", host)?.apply()
        apiClient.baseUrl = buildBaseUrl(host, _apiPort.value)
    }

    fun setApiPort(port: String) {
        _apiPort.value = port
        prefs?.edit()?.putString("api_port", port)?.apply()
        apiClient.baseUrl = buildBaseUrl(_apiHost.value, port)
    }

    fun resetApiConfig() {
        val host = BuildConfig.SCAN_API_HOST
        val port = BuildConfig.SCAN_API_PORT
        _apiHost.value = host
        _apiPort.value = port
        prefs?.edit()?.remove("api_host")?.remove("api_port")?.apply()
        apiClient.baseUrl = buildBaseUrl(host, port)
    }

    fun onBarcodeDetected(rawValue: String) {
        if (_loading.value) return

        val gate = _gateName.value
        if (gate.isBlank()) {
            _toastMessage.value = "Set a gate name in Settings before scanning"
            return
        }

        val payload = try {
            verifier.verify(rawValue, expectedEid = _selectedEvent.value.id)
        } catch (e: MalformedTokenException)   { handleResult(ScanResult.FakeTicket()); return }
        catch (e: UnknownKeyException)         { handleResult(ScanResult.FakeTicket()); return }
        catch (e: InvalidSignatureException)   { handleResult(ScanResult.FakeTicket()); return }
        catch (e: ExpiredException) {
            val validFor  = formatTimestamp(e.expSeconds)
            val scannedAt = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date())
            handleResult(ScanResult.Expired(e.jti, e.tier, validFor, scannedAt)); return
        }
        catch (e: WrongEventException) {
            handleResult(ScanResult.WrongEntrance(
                ticketId   = "",
                ticketTier = e.ticketTier,
                gateTier   = _selectedEvent.value.name,
            )); return
        }
        catch (_: TicketVerificationException) { handleResult(ScanResult.FakeTicket()); return }

        _loading.value = true
        viewModelScope.launch {
            try {
                val apiResult = apiClient.recordScan(payload.jti, payload.eid, gate)
                when (apiResult) {
                    is ScanApiResult.Ok ->
                        handleResult(ScanResult.Valid(payload.jti, payload.tier, payload.admits))
                    is ScanApiResult.AlreadyUsed ->
                        handleResult(ScanResult.AlreadyUsed(
                            payload.jti, payload.tier,
                            apiResult.firstScanTime, apiResult.firstScanGate,
                        ))
                    is ScanApiResult.NotFound ->
                        handleResult(ScanResult.FakeTicket(payload.jti))
                    is ScanApiResult.AuthError -> {
                        _toastMessage.value = "Auth error — contact supervisor"
                        _scanResetKey.value++
                    }
                    is ScanApiResult.ServerError -> {
                        _toastMessage.value = "Server error, try again"
                        _scanResetKey.value++
                    }
                    is ScanApiResult.NetworkError -> {
                        _toastMessage.value = "No connection"
                        _scanResetKey.value++
                    }
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun onDemoResult(result: ScanResult) { handleResult(result) }

    private fun handleResult(result: ScanResult) {
        _currentResult.value = result
        if (result is ScanResult.Valid) _scanCount.value++
        _screen.value = Screen.Result
    }

    private fun formatTimestamp(epochSeconds: Long): String =
        SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(epochSeconds * 1000))

    fun onNext()        { _screen.value = Screen.Scanner }
    fun openSettings()  { _screen.value = Screen.Settings }
    fun closeSettings() { _screen.value = Screen.Scanner }
    fun switchEvent()   { _screen.value = Screen.EventPicker }
    fun selectEvent(event: Event) { _selectedEvent.value = event }
    fun startScanning() { _screen.value = Screen.Scanner }
    fun setScanMode(mode: ScanMode)  { _scanMode.value = mode }
    fun setSoundEnabled(v: Boolean)  { _soundEnabled.value = v }
    fun setVibrateEnabled(v: Boolean){ _vibrateEnabled.value = v }

    companion object {
        fun buildBaseUrl(host: String, port: String) = "${host.trimEnd('/')}:$port"
    }
}
