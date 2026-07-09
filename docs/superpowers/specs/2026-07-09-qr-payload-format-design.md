# QR Payload Format Design

**Date**: 2026-07-09
**Project**: kuunyi-scanner (Ticket Verify Android app)
**Status**: Approved

## Overview

Define the QR code payload format for ticket scanning, including cryptographic signing and the on-device verification flow. Every scan requires an online call to the backend; the QR signature provides instant on-device forgery detection before the network round-trip.

## Decisions

- **Verification**: Online-required. Every scan POSTs to the backend, which records the scan and prevents cross-gate duplicate use.
- **Signing**: Ed25519 asymmetric signing. The backend holds the private key; the app ships with the public key embedded. No secret on device.
- **Format**: JWT (JSON Web Token), standard `alg: EdDSA`.

## QR Payload Format

Each ticket QR encodes a compact JWT. JWT header:

```json
{ "alg": "EdDSA", "typ": "JWT", "kid": "v1" }
```

`kid` identifies the key version, enabling future key rotation without breaking in-field apps.

Example decoded payload:

```json
{
  "jti": "TKT-9F2A31",
  "eid": "evt-summer-2026",
  "tier": "VIP",
  "admits": 1,
  "iat": 1752000000,
  "exp": 1752086400
}
```

| Field | Type | Description |
|---|---|---|
| `jti` | string | Ticket ID — globally unique; used as the server-side scan key |
| `eid` | string | Event ID — must match the event selected in the app |
| `tier` | string | Ticket tier (e.g., GA, VIP) — informational, taken from the JWT locally |
| `admits` | int | Number of people this ticket admits — must be >= 1; if 0 or negative, display as 1 |
| `iat` | int | Issued-at (Unix seconds) |
| `exp` | int | Expiry (Unix seconds) |

**QR encoding**: Use error correction level **M** (15% recovery). Typical JWT payload is ~300–400 chars — well within QR capacity at this level.

### `admits` behaviour
`admits` is displayed on the Valid result screen ("1 admit", "2 admits"). A single scan always constitutes one gate event regardless of `admits`. The backend does not decrement a counter; the operator reads the count and admits that many people. `admits` comes from the locally-decoded JWT — it is not returned by the server.

## Public Key Management

The app ships with a map of key versions to Ed25519 public keys:

```kotlin
val PUBLIC_KEYS = mapOf(
    "v1" to "<base64url-encoded-public-key>"
)
```

The `kid` in the JWT header selects which key to use for verification. If `kid` is absent or unknown, verification fails → `FakeTicket`.

**Key rotation**: Adding a new key (`"v2"`) requires an app update. Key rotation is planned per-season; an emergency release is required on key compromise. There is no in-app revocation mechanism for old installs — this is accepted risk for MVP. Old app versions holding only `v1` cannot verify `v2`-signed tickets; after a key rotation the backend should reject requests from app versions below the minimum version (via the `X-App-Version` header described in the API section).

## Gate Identity

Each scanner device is assigned a **gate name** (e.g., `"Gate A"`, `"VIP Entrance"`) configured by the operator in the Settings screen before scanning begins. This value is persisted in `SharedPreferences` and included in every POST body as `gate`. If no gate name is set, the app shows a configuration warning and disables scanning.

## API Authentication

Every POST to `/scans` includes a static **API key** in the `Authorization` header:

```
Authorization: Bearer <api-key>
X-App-Version: <versionCode>
```

The API key is stored in `BuildConfig.SCAN_API_KEY`, injected at build time via `local.properties` (not committed to source control). Debug builds use a separate test key. `X-App-Version` allows the backend to reject outdated app versions after a key rotation.

## Verification Flow

Steps 1–4 are instant (no network). Step 5 is the only network call. `tier` and `admits` used in results always come from the locally-decoded `TicketPayload`, not from the API response.

```
1. Decode JWT structure and read kid from header
   → malformed / kid unknown → FakeTicket

2. Verify Ed25519 signature with the key matching kid
   → invalid signature → FakeTicket

3. Check exp vs current device time
   → expired → Expired(tier, validFor=formatted exp window, scannedAt=now)
   Note: expired tickets are NOT posted to the backend. The on-device exp
   check is intentionally terminal — turning away an expired ticket does not
   need a server-side record. The server validates exp only for tickets that
   somehow bypass step 3 (e.g., via clock tampering), ensuring they are
   rejected at the server.

4. Check eid matches selectedEvent.id (TicketVerifier accepts expectedEid as a parameter)
   → mismatch → WrongEntrance(ticketTier=tier, gateName=selectedEvent.name)
   Note: "wrong event" and "wrong gate within the right event" are treated
   identically — the operator is shown the ticket's tier and the gate's event
   name and told to send the person to the correct entrance.

5. POST /scans { jti, eid, gate } to backend (5s connect timeout, 10s read timeout)
   → 200 OK        → Valid(ticketId=jti, tier, admits)   [tier/admits from local JWT]
   → 409 Conflict  → AlreadyUsed(ticketId, tier, firstScanTime, firstScanGate)
   → 400 Bad Request  → FakeTicket (malformed payload reached server)
   → 401/403       → toast "Auth error — contact supervisor", stay on Scanner
   → 404           → FakeTicket (ticket ID not in system)
   → 5xx           → toast "Server error, try again", stay on Scanner
   → timeout / no network → toast "No connection", stay on Scanner
```

**Race condition**: The backend must use an atomic compare-and-swap (or serializable write) when recording a scan, so two simultaneous scans of the same ticket at different gates cannot both receive 200.

**Camera analyzer**: The analyzer is paused for the duration of step 5 (network call in-flight). It resumes only when the result screen is dismissed — i.e., when the user taps "Next ticket" or "Scan again" and returns to the Scanner screen. There is no scan queue.

## API Contract

### POST /scans

**Request**:
```json
{
  "jti": "TKT-9F2A31",
  "eid": "evt-summer-2026",
  "gate": "Gate A"
}
```

**200 OK** (ticket accepted):
```json
{ "status": "ok" }
```
`tier` and `admits` are taken from the locally-decoded JWT, not from this response.

**409 Conflict** (already used):
```json
{
  "status": "already_used",
  "firstScanTime": "Today · 8:52 AM",
  "firstScanGate": "Gate — Main Arena"
}
```

All other error responses: no body parsing required — HTTP status code is sufficient to determine the result.

## Code Structure

### `TicketVerifier.kt`
Pure Kotlin class. No Android dependencies. Fully unit-testable.

- Holds the `PUBLIC_KEYS` map (kid → Ed25519 public key bytes)
- `fun verify(raw: String, expectedEid: String): TicketPayload`
  - Decodes and verifies the JWT
  - Checks `exp` against current time
  - Checks `eid == expectedEid`
  - Clamps `admits` to minimum 1
  - Throws typed exceptions: `InvalidSignatureException`, `ExpiredException`, `WrongEventException`, `MalformedTokenException`, `UnknownKeyException`
- Returns `TicketPayload(jti, eid, tier, admits, exp)`

### `ScanApiClient.kt`
Thin HTTP client. Suspending functions for coroutine compatibility.

- `suspend fun recordScan(jti: String, eid: String, gate: String): ScanApiResult`
- Returns sealed class:
  - `ScanApiResult.Ok`
  - `ScanApiResult.AlreadyUsed(firstScanTime: String, firstScanGate: String)`
  - `ScanApiResult.NotFound`
  - `ScanApiResult.AuthError`
  - `ScanApiResult.ServerError`
  - `ScanApiResult.NetworkError`
- Base URL: `BuildConfig.SCAN_API_BASE_URL`; API key: `BuildConfig.SCAN_API_KEY`
- Uses `HttpURLConnection` with 5s connect timeout and 10s read timeout

### `ScannerViewModel.kt` (updated)
- `onBarcodeDetected(raw)` becomes a coroutine-based function:
  1. Calls `TicketVerifier.verify(raw, selectedEvent.id)` — catches exceptions → maps to `ScanResult`
  2. Emits `_loading = true`
  3. Calls `ScanApiClient.recordScan(jti, eid, gate)`
  4. Maps `ScanApiResult` to `ScanResult`; emits `_loading = false`; navigates to Result screen
- New `_loading: MutableStateFlow<Boolean>` exposed to Scanner UI for spinner overlay

### `app/build.gradle.kts` (updated)
- Add `com.nimbusds:nimbus-jose-jwt` — standard Java JWT/JWS library with Ed25519 support (~200KB AAR)

## Error Handling Summary

| Situation | Result shown |
|---|---|
| Malformed QR / not a JWT | FakeTicket |
| Unknown kid | FakeTicket |
| Bad signature | FakeTicket |
| Valid signature but expired | Expired |
| Valid but wrong event | WrongEntrance |
| Valid, not in system — server 404 | FakeTicket |
| Valid, not used — server 200 | Valid |
| Valid, already used — server 409 | AlreadyUsed |
| Server 400 | FakeTicket |
| Server 401/403 | Toast "Auth error — contact supervisor" |
| Server 5xx | Toast "Server error, try again" |
| Network error / timeout | Toast "No connection" |

## Testing

- `TicketVerifier` unit tests: valid JWT, bad signature, expired, wrong event, malformed string, unknown kid, admits=0 — all on JVM with no Android context
- `ScanApiClient` unit tests: mock HTTP responses for 200, 409, 400, 401, 404, 500, timeout
- `ScannerViewModel` integration tests: verify correct `ScanResult` for each path, loading state transitions, gate name missing warning
