# QR Payload Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the demo barcode cycling in `ScannerViewModel` with real Ed25519-signed JWT verification and a backend scan API call.

**Architecture:** `TicketVerifier` (pure Kotlin, no Android deps) verifies the JWT signature and claims offline. If valid, `ScanApiClient` POSTs to the backend to record the scan and check for duplicates. `ScannerViewModel` orchestrates both, mapping results to the existing `ScanResult` sealed class. A `scanResetKey` mechanism resets the camera analyzer after non-fatal network errors so scanning can resume without leaving the screen.

**Tech Stack:** Nimbus JOSE+JWT 9.37.3 (Ed25519 JWT), `HttpURLConnection` (no HTTP lib), MockWebServer 4.12.0 (test only), JUnit 4, kotlinx-coroutines-test.

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `app/build.gradle.kts` | Modify | Add nimbus-jose-jwt, MockWebServer, BuildConfig fields |
| `local.properties` | Modify | Add API URL, API key, public key placeholders |
| `app/src/main/java/com/kuunyi/scanner/data/TicketPayload.kt` | Create | JWT payload data class + typed exception hierarchy |
| `app/src/main/java/com/kuunyi/scanner/util/TicketVerifier.kt` | Create | Ed25519 JWT verifier — pure Kotlin, no Android deps, `open` methods |
| `app/src/main/java/com/kuunyi/scanner/network/ScanApiClient.kt` | Create | HTTP POST /scans + `ScanApiResult` sealed class, `open` method |
| `app/src/main/java/com/kuunyi/scanner/viewmodel/ScannerViewModel.kt` | Modify | Real verification flow, loading + scanResetKey state, gate name |
| `app/src/main/java/com/kuunyi/scanner/ui/screens/ScannerScreen.kt` | Modify | Loading spinner overlay, camera resetKey wiring |
| `app/src/main/java/com/kuunyi/scanner/ui/screens/SettingsScreen.kt` | Modify | Gate name input row |
| `app/src/test/java/com/kuunyi/scanner/util/TicketVerifierTest.kt` | Create | TicketVerifier unit tests |
| `app/src/test/java/com/kuunyi/scanner/network/ScanApiClientTest.kt` | Create | ScanApiClient unit tests |
| `app/src/test/java/com/kuunyi/scanner/viewmodel/ScannerViewModelTest.kt` | Modify | Replace with updated tests for injectable verifier + client |

---

## Task 1: Add Dependencies and BuildConfig Fields

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `local.properties`

- [ ] **Step 1: Update `app/build.gradle.kts`**

In `android { buildFeatures { } }`, add `buildConfig = true`:

```kotlin
buildFeatures {
    compose = true
    buildConfig = true
}
```

In `android { defaultConfig { } }`, add BuildConfig fields:

```kotlin
buildConfigField("String", "SCAN_API_BASE_URL",
    "\"${project.findProperty("SCAN_API_BASE_URL") ?: "https://api.example.com"}\"")
buildConfigField("String", "SCAN_API_KEY",
    "\"${project.findProperty("SCAN_API_KEY") ?: "dev-api-key"}\"")
buildConfigField("String", "ED25519_PUBLIC_KEY_V1",
    "\"${project.findProperty("ED25519_PUBLIC_KEY_V1") ?: ""}\"")
```

In `dependencies { }`, add:

```kotlin
implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

- [ ] **Step 2: Append to `local.properties`** (do not overwrite the `sdk.dir` line)

```properties
SCAN_API_BASE_URL=https://api.example.com
SCAN_API_KEY=dev-api-key-replace-me
ED25519_PUBLIC_KEY_V1=replace-with-real-base64url-public-key
```

- [ ] **Step 3: Sync Gradle** — File → Sync Project with Gradle Files

Expected: BUILD SUCCESSFUL. `BuildConfig` is now generated with the three new fields.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts local.properties
git commit -m "feat: add nimbus-jose-jwt, MockWebServer, BuildConfig scan fields"
```

---

## Task 2: Create TicketPayload and Exceptions

**Files:**
- Create: `app/src/main/java/com/kuunyi/scanner/data/TicketPayload.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.kuunyi.scanner.data

data class TicketPayload(
    val jti: String,
    val eid: String,
    val tier: String,
    val admits: Int,
    val expSeconds: Long,
)

sealed class TicketVerificationException(message: String) : Exception(message)
class MalformedTokenException(msg: String = "Not a valid JWT") : TicketVerificationException(msg)
class UnknownKeyException(kid: String) : TicketVerificationException("Unknown key id: $kid")
class InvalidSignatureException : TicketVerificationException("Signature invalid")

/** Thrown when the token is expired. Carries jti and tier so the result screen can display them. */
class ExpiredException(
    val jti: String,
    val tier: String,
    val expSeconds: Long,
) : TicketVerificationException("Token expired")

/** Thrown when eid does not match the expected event. Carries ticketTier for the result screen. */
class WrongEventException(
    val ticketTier: String,
    val ticketEid: String,
    val expectedEid: String,
) : TicketVerificationException("Event mismatch")
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/kuunyi/scanner/data/TicketPayload.kt
git commit -m "feat: add TicketPayload data class and typed verification exceptions"
```

---

## Task 3: Implement TicketVerifier (TDD)

**Files:**
- Create: `app/src/test/java/com/kuunyi/scanner/util/TicketVerifierTest.kt`
- Create: `app/src/main/java/com/kuunyi/scanner/util/TicketVerifier.kt`

- [ ] **Step 1: Create `TicketVerifierTest.kt`**

```kotlin
package com.kuunyi.scanner.util

import com.kuunyi.scanner.data.*
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.util.*

class TicketVerifierTest {

    companion object {
        private lateinit var privateJwk: com.nimbusds.jose.jwk.OctetKeyPair
        private lateinit var publicKeyBase64Url: String
        private lateinit var verifier: TicketVerifier

        @BeforeClass @JvmStatic fun setup() {
            privateJwk = OctetKeyPairGenerator(Curve.Ed25519).keyID("v1").generate()
            publicKeyBase64Url = privateJwk.x.toString()
            verifier = TicketVerifier(publicKeys = mapOf("v1" to publicKeyBase64Url))
        }

        fun makeJwt(
            jti: String = "TKT-001",
            eid: String = "evt-summer-2026",
            tier: String = "VIP",
            admits: Int = 1,
            expOffsetMs: Long = 3_600_000L,
        ): String {
            val claims = JWTClaimsSet.Builder()
                .jwtID(jti)
                .claim("eid", eid)
                .claim("tier", tier)
                .claim("admits", admits)
                .issueTime(Date())
                .expirationTime(Date(System.currentTimeMillis() + expOffsetMs))
                .build()
            val header = JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("v1").build()
            val jwt = SignedJWT(header, claims)
            jwt.sign(Ed25519Signer(privateJwk))
            return jwt.serialize()
        }
    }

    @Test fun `valid JWT returns TicketPayload`() {
        val p = verifier.verify(makeJwt(), "evt-summer-2026")
        assertEquals("TKT-001", p.jti)
        assertEquals("VIP", p.tier)
        assertEquals(1, p.admits)
    }

    @Test fun `admits 0 is clamped to 1`() {
        assertEquals(1, verifier.verify(makeJwt(admits = 0), "evt-summer-2026").admits)
    }

    @Test fun `admits negative is clamped to 1`() {
        assertEquals(1, verifier.verify(makeJwt(admits = -3), "evt-summer-2026").admits)
    }

    @Test(expected = InvalidSignatureException::class)
    fun `tampered signature throws InvalidSignatureException`() {
        val parts = makeJwt().split(".")
        val bad = "${parts[0]}.${parts[1]}.${"A".repeat(86)}"
        verifier.verify(bad, "evt-summer-2026")
    }

    @Test(expected = ExpiredException::class)
    fun `expired JWT throws ExpiredException`() {
        verifier.verify(makeJwt(expOffsetMs = -1_000L), "evt-summer-2026")
    }

    @Test fun `ExpiredException carries jti and tier`() {
        try {
            verifier.verify(makeJwt(jti = "TKT-XYZ", tier = "GA", expOffsetMs = -1_000L), "evt-summer-2026")
            fail("Expected ExpiredException")
        } catch (e: ExpiredException) {
            assertEquals("TKT-XYZ", e.jti)
            assertEquals("GA", e.tier)
        }
    }

    @Test(expected = WrongEventException::class)
    fun `mismatched eid throws WrongEventException`() {
        verifier.verify(makeJwt(eid = "evt-other"), "evt-summer-2026")
    }

    @Test fun `WrongEventException carries ticketTier`() {
        try {
            verifier.verify(makeJwt(tier = "GA", eid = "evt-other"), "evt-summer-2026")
            fail("Expected WrongEventException")
        } catch (e: WrongEventException) {
            assertEquals("GA", e.ticketTier)
        }
    }

    @Test(expected = MalformedTokenException::class)
    fun `random string throws MalformedTokenException`() {
        verifier.verify("not-a-jwt", "evt-summer-2026")
    }

    @Test(expected = MalformedTokenException::class)
    fun `empty string throws MalformedTokenException`() {
        verifier.verify("", "evt-summer-2026")
    }

    @Test(expected = UnknownKeyException::class)
    fun `JWT with unknown kid throws UnknownKeyException`() {
        val claims = JWTClaimsSet.Builder()
            .jwtID("TKT-001").claim("eid", "evt-summer-2026")
            .claim("tier", "VIP").claim("admits", 1)
            .expirationTime(Date(System.currentTimeMillis() + 3_600_000))
            .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("v99").build(), claims)
        jwt.sign(Ed25519Signer(privateJwk))
        verifier.verify(jwt.serialize(), "evt-summer-2026")
    }
}
```

- [ ] **Step 2: Run — expect FAILURE (ClassNotFoundException)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.kuunyi.scanner.util.TicketVerifierTest"
```

- [ ] **Step 3: Create `TicketVerifier.kt`**

Note: `verify()` is `open` so tests can subclass it with a fake.

```kotlin
package com.kuunyi.scanner.util

import com.kuunyi.scanner.data.*
import com.nimbusds.jose.Base64URL
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jwt.SignedJWT
import java.text.ParseException

open class TicketVerifier(
    private val publicKeys: Map<String, String>,
) {
    open fun verify(raw: String, expectedEid: String): TicketPayload {
        val jwt = try {
            SignedJWT.parse(raw)
        } catch (_: ParseException) {
            throw MalformedTokenException()
        }

        val kid = jwt.header.keyID ?: throw MalformedTokenException("Missing kid")
        val keyBase64Url = publicKeys[kid] ?: throw UnknownKeyException(kid)

        val jwk = OctetKeyPair.Builder(Curve.Ed25519, Base64URL(keyBase64Url)).build()
        if (!jwt.verify(Ed25519Verifier(jwk))) throw InvalidSignatureException()

        val claims = jwt.jwtClaimsSet

        // Parse jti and tier first — needed by ExpiredException and WrongEventException
        val jti = claims.jwtid ?: throw MalformedTokenException("Missing jti")
        val tier = claims.getStringClaim("tier") ?: throw MalformedTokenException("Missing tier")
        val admitsRaw = (claims.getClaim("admits") as? Number)?.toInt() ?: 1
        val admits = maxOf(1, admitsRaw)

        val expSeconds = claims.expirationTime?.time?.div(1000)
            ?: throw MalformedTokenException("Missing exp")
        if (System.currentTimeMillis() / 1000 > expSeconds) {
            throw ExpiredException(jti = jti, tier = tier, expSeconds = expSeconds)
        }

        val eid = claims.getStringClaim("eid") ?: throw MalformedTokenException("Missing eid")
        if (eid != expectedEid) throw WrongEventException(ticketTier = tier, ticketEid = eid, expectedEid = expectedEid)

        return TicketPayload(jti = jti, eid = eid, tier = tier, admits = admits, expSeconds = expSeconds)
    }
}
```

- [ ] **Step 4: Run — expect all pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.kuunyi.scanner.util.TicketVerifierTest"
```

Expected: BUILD SUCCESSFUL — 11 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kuunyi/scanner/util/TicketVerifier.kt \
        app/src/test/java/com/kuunyi/scanner/util/TicketVerifierTest.kt
git commit -m "feat: implement TicketVerifier with Ed25519 JWT verification (TDD)"
```

---

## Task 4: Implement ScanApiClient (TDD)

**Files:**
- Create: `app/src/test/java/com/kuunyi/scanner/network/ScanApiClientTest.kt`
- Create: `app/src/main/java/com/kuunyi/scanner/network/ScanApiClient.kt`

- [ ] **Step 1: Create `ScanApiClientTest.kt`**

```kotlin
package com.kuunyi.scanner.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScanApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ScanApiClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = ScanApiClient(
            baseUrl = server.url("").toString().trimEnd('/'),
            apiKey = "test-key",
            versionCode = 1,
        )
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `200 returns Ok`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        assertTrue(client.recordScan("TKT-001", "evt-1", "Gate A") is ScanApiResult.Ok)
    }

    @Test fun `409 returns AlreadyUsed with parsed fields`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody(
            """{"status":"already_used","firstScanTime":"Today · 8:52 AM","firstScanGate":"Gate — Main Arena"}"""
        ))
        val r = client.recordScan("TKT-001", "evt-1", "Gate A") as ScanApiResult.AlreadyUsed
        assertEquals("Today · 8:52 AM", r.firstScanTime)
        assertEquals("Gate — Main Arena", r.firstScanGate)
    }

    @Test fun `400 returns NotFound`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400))
        assertTrue(client.recordScan("TKT-001", "evt-1", "Gate A") is ScanApiResult.NotFound)
    }

    @Test fun `404 returns NotFound`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(client.recordScan("TKT-001", "evt-1", "Gate A") is ScanApiResult.NotFound)
    }

    @Test fun `401 returns AuthError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(client.recordScan("TKT-001", "evt-1", "Gate A") is ScanApiResult.AuthError)
    }

    @Test fun `403 returns AuthError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(client.recordScan("TKT-001", "evt-1", "Gate A") is ScanApiResult.AuthError)
    }

    @Test fun `500 returns ServerError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(client.recordScan("TKT-001", "evt-1", "Gate A") is ScanApiResult.ServerError)
    }

    @Test fun `connection refused returns NetworkError`() = runBlocking {
        server.shutdown()
        assertTrue(client.recordScan("TKT-001", "evt-1", "Gate A") is ScanApiResult.NetworkError)
    }

    @Test fun `POST body contains jti, eid, gate`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        client.recordScan("TKT-XYZ", "evt-abc", "VIP Gate")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("TKT-XYZ"))
        assertTrue(body.contains("evt-abc"))
        assertTrue(body.contains("VIP Gate"))
    }

    @Test fun `Authorization header is set`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        client.recordScan("TKT-001", "evt-1", "Gate A")
        assertEquals("Bearer test-key", server.takeRequest().getHeader("Authorization"))
    }
}
```

- [ ] **Step 2: Run — expect FAILURE**

```bash
./gradlew :app:testDebugUnitTest --tests "com.kuunyi.scanner.network.ScanApiClientTest"
```

- [ ] **Step 3: Create `ScanApiClient.kt`**

Note: `recordScan()` is `open` so tests can subclass it with a fake.

```kotlin
package com.kuunyi.scanner.network

sealed class ScanApiResult {
    object Ok : ScanApiResult()
    data class AlreadyUsed(val firstScanTime: String, val firstScanGate: String) : ScanApiResult()
    object NotFound : ScanApiResult()
    object AuthError : ScanApiResult()
    object ServerError : ScanApiResult()
    object NetworkError : ScanApiResult()
}

open class ScanApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val versionCode: Int,
) {
    open suspend fun recordScan(jti: String, eid: String, gate: String): ScanApiResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("$baseUrl/scans")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("X-App-Version", versionCode.toString())
                    connectTimeout = 5_000
                    readTimeout = 10_000
                    doOutput = true
                }
                val body = """{"jti":"$jti","eid":"$eid","gate":"$gate"}"""
                conn.outputStream.use { it.write(body.toByteArray()) }
                when (conn.responseCode) {
                    200 -> ScanApiResult.Ok
                    409 -> {
                        val json = conn.inputStream.bufferedReader().readText()
                        val time = Regex(""""firstScanTime"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: ""
                        val g = Regex(""""firstScanGate"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: ""
                        ScanApiResult.AlreadyUsed(time, g)
                    }
                    400, 404 -> ScanApiResult.NotFound
                    401, 403 -> ScanApiResult.AuthError
                    else -> ScanApiResult.ServerError
                }
            } catch (_: Exception) {
                ScanApiResult.NetworkError
            }
        }
}
```

- [ ] **Step 4: Run — expect all pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.kuunyi.scanner.network.ScanApiClientTest"
```

Expected: BUILD SUCCESSFUL — 10 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kuunyi/scanner/network/ \
        app/src/test/java/com/kuunyi/scanner/network/
git commit -m "feat: implement ScanApiClient with POST /scans and full status code handling (TDD)"
```

---

## Task 5: Update ScannerViewModel

**Files:**
- Modify: `app/src/main/java/com/kuunyi/scanner/viewmodel/ScannerViewModel.kt`
- Modify: `app/src/test/java/com/kuunyi/scanner/viewmodel/ScannerViewModelTest.kt`

**Note on event IDs:** The event list changes from `id = "1"` / `"2"` to `id = "evt-summer-2026"` / `"evt-night-aug"`. This is intentional — the `id` field is used as `eid` in JWTs and must match backend event IDs. Demo data was placeholder values.

- [ ] **Step 1: Replace `ScannerViewModel.kt` entirely**

```kotlin
package com.kuunyi.scanner.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuunyi.scanner.BuildConfig
import com.kuunyi.scanner.data.*
import com.kuunyi.scanner.network.ScanApiClient
import com.kuunyi.scanner.network.ScanApiResult
import com.kuunyi.scanner.util.TicketVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ScannerViewModel(
    private val verifier: TicketVerifier = TicketVerifier(
        publicKeys = mapOf("v1" to BuildConfig.ED25519_PUBLIC_KEY_V1)
    ),
    private val apiClient: ScanApiClient = ScanApiClient(
        baseUrl = BuildConfig.SCAN_API_BASE_URL,
        apiKey = BuildConfig.SCAN_API_KEY,
        versionCode = BuildConfig.VERSION_CODE,
    ),
) : ViewModel() {

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
            val apiResult = apiClient.recordScan(payload.jti, payload.eid, gate)
            _loading.value = false
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
}
```

- [ ] **Step 2: Replace `ScannerViewModelTest.kt`**

**Note:** The previous tests that cycled through demo results are intentionally removed — that logic no longer exists. The new tests inject fakes. Existing tests for navigation, settings, and mode toggles are preserved.

```kotlin
package com.kuunyi.scanner.viewmodel

import com.kuunyi.scanner.data.*
import com.kuunyi.scanner.network.ScanApiClient
import com.kuunyi.scanner.network.ScanApiResult
import com.kuunyi.scanner.util.TicketVerifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
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

    /** Helper: ViewModel with fakes, gate name pre-set so scans are not blocked. */
    private fun vm(
        verifier: TicketVerifier = FakeVerifier(),
        api: ScanApiClient = FakeApiClient(),
    ) = ScannerViewModel(verifier = verifier, apiClient = api).also { it.setGateName("Gate A") }

    // --- Initial state ---

    @Test fun `initial screen is EventPicker`() {
        assertEquals(Screen.EventPicker, ScannerViewModel().screen.value)
    }

    @Test fun `initial scan count is 0`() {
        assertEquals(0, ScannerViewModel().scanCount.value)
    }

    @Test fun `initial selected event is the first`() {
        val v = ScannerViewModel()
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
```

- [ ] **Step 3: Run all tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/kuunyi/scanner/viewmodel/ \
        app/src/test/java/com/kuunyi/scanner/viewmodel/
git commit -m "feat: wire TicketVerifier + ScanApiClient into ScannerViewModel with loading and reset key"
```

---

## Task 6: Update ScannerScreen — Loading Overlay and Camera Reset

**Files:**
- Modify: `app/src/main/java/com/kuunyi/scanner/ui/screens/ScannerScreen.kt`

The `CameraPreview` composable uses an `AtomicBoolean detected` to prevent double-scanning. When a non-fatal error occurs (network down, server error), the ViewModel increments `scanResetKey` instead of navigating away. Passing `scanResetKey` as a key to `DisposableEffect` causes the camera analyzer to be torn down and rebuilt, resetting `detected` so the user can scan again.

- [ ] **Step 1: Update `ScannerScreen` composable — add state observations and toast**

Add these observations after `val scanCount by vm.scanCount...`:

```kotlin
val loading by vm.loading.collectAsStateWithLifecycle()
val toastMessage by vm.toastMessage.collectAsStateWithLifecycle()
val scanResetKey by vm.scanResetKey.collectAsStateWithLifecycle()
```

Add a `LaunchedEffect` for toasts immediately after the `SideEffect` block:

```kotlin
LaunchedEffect(toastMessage) {
    toastMessage?.let {
        android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
        vm.clearToast()
    }
}
```

Pass `scanResetKey` to `CameraPreview`:

```kotlin
CameraPreview(
    scanEnabled = scanEnabled,
    scanResetKey = scanResetKey,
    onBarcodeDetected = { ... },
    modifier = Modifier.fillMaxSize(),
)
```

Add loading overlay as the **last child** of the outermost `Box` (so it renders on top):

```kotlin
if (loading) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        androidx.compose.material3.CircularProgressIndicator(color = Color.White)
    }
}
```

- [ ] **Step 2: Update `CameraPreview` signature and `DisposableEffect`**

Change the function signature to accept `scanResetKey`:

```kotlin
@Composable
private fun CameraPreview(
    scanEnabled: Boolean,
    scanResetKey: Int,
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
```

Move `val detected = remember { AtomicBoolean(false) }` **inside** `DisposableEffect`, and add `scanResetKey` as a key so it resets when the ViewModel signals:

```kotlin
DisposableEffect(lifecycleOwner, scanResetKey) {
    val detected = AtomicBoolean(false)   // fresh on each key change
    // ... rest of camera setup unchanged ...
}
```

Remove the old `val detected = remember { AtomicBoolean(false) }` line that was outside the `DisposableEffect`.

- [ ] **Step 3: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/kuunyi/scanner/ui/screens/ScannerScreen.kt
git commit -m "feat: add loading overlay and camera reset wiring to ScannerScreen"
```

---

## Task 7: Update Settings Screen — Gate Name Field

**Files:**
- Modify: `app/src/main/java/com/kuunyi/scanner/ui/screens/SettingsScreen.kt`

- [ ] **Step 1: Add imports**

```kotlin
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
```

- [ ] **Step 2: Add gate name observation**

After `val scanCount by vm.scanCount...`:

```kotlin
val gateName by vm.gateName.collectAsStateWithLifecycle()
```

- [ ] **Step 3: Add GATE section**

Insert after the SCANNING section and before the EVENT `SectionLabel`:

```kotlin
SectionLabel("GATE")

Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
) {
    OutlinedTextField(
        value = gateName,
        onValueChange = { vm.setGateName(it) },
        label = { Text("Gate name", fontSize = 13.sp) },
        placeholder = { Text("e.g. Gate A, VIP Entrance", fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
        ),
        isError = gateName.isBlank(),
    )
    if (gateName.isBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Required — scanning is disabled without a gate name",
            fontSize = 11.sp,
            color = com.kuunyi.scanner.ui.theme.Red,
        )
    }
}
HorizontalDivider(color = GrayF0)
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run full test suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 6: Commit and push**

```bash
git add app/src/main/java/com/kuunyi/scanner/ui/screens/SettingsScreen.kt
git commit -m "feat: add gate name field to Settings screen"
git push
```

---

## Summary

After all tasks:

- Real Ed25519 JWT verification replaces demo cycling — `FakeTicket` for forgeries, `Expired` for expired tickets, `WrongEntrance` for wrong-event tickets
- Backend API call on every valid scan — `AlreadyUsed` on 409, error toast for network/server failures
- Camera analyzer resets automatically after non-fatal errors so the operator can scan again without leaving the screen
- Gate name required in Settings before scanning begins
- Loading spinner shown during the network call
- Unit test coverage: TicketVerifier (11), ScanApiClient (10), ScannerViewModel (22)

**To connect a real backend:** update `local.properties` with actual `SCAN_API_BASE_URL`, `SCAN_API_KEY`, and `ED25519_PUBLIC_KEY_V1` values.
