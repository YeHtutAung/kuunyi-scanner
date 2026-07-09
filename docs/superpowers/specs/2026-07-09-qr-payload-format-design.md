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

Each ticket QR encodes a compact JWT. Example decoded payload:

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
| `tier` | string | Ticket tier (e.g., GA, VIP) |
| `admits` | int | Number of people this ticket admits |
| `iat` | int | Issued-at (Unix seconds) |
| `exp` | int | Expiry (Unix seconds) — scanning after this yields Expired |

JWT header: `{ "alg": "EdDSA", "typ": "JWT" }`

## Verification Flow

Steps run in this order on every scan. Steps 1–4 are instant (no network). Step 5 is the only network call.

```
1. Decode JWT structure
   → malformed → FakeTicket

2. Verify Ed25519 signature with embedded public key
   → invalid signature → FakeTicket

3. Check exp vs current device time
   → expired → Expired(tier, validFor="<eid> event window", scannedAt=now)

4. Check eid matches selected event
   → mismatch → WrongEntrance(ticketTier=tier, gateTier=selectedEvent.id)

5. POST /scans { jti, eid, gate } to backend
   → 200 OK        → Valid(ticketId=jti, tier, admits)
   → 409 Conflict  → AlreadyUsed(ticketId, tier, firstScanTime, firstScanGate)
   → network error → show "No connection" toast, stay on Scanner screen
```

## Code Structure

### `TicketVerifier.kt`
Pure Kotlin class. No Android dependencies. Fully unit-testable.

- Holds the Ed25519 public key as a Base64-encoded constant
- `fun verify(raw: String): TicketPayload` — decodes and verifies the JWT
- Throws typed exceptions: `InvalidSignatureException`, `ExpiredException`, `WrongEventException`, `MalformedTokenException`
- Returns `TicketPayload(jti, eid, tier, admits, exp)`

### `ScanApiClient.kt`
Thin HTTP client. Suspending functions for coroutine compatibility.

- `suspend fun recordScan(jti: String, eid: String, gate: String): ScanApiResult`
- Returns sealed class: `ScanApiResult.Ok`, `ScanApiResult.AlreadyUsed(firstScanTime, firstScanGate)`, `ScanApiResult.NetworkError`
- Base URL sourced from `BuildConfig.SCAN_API_BASE_URL` (different for debug/release)
- Uses `HttpURLConnection` — no extra HTTP library needed

### `ScannerViewModel.kt` (updated)
- `parseBarcode(raw)` replaced with a coroutine that calls `TicketVerifier.verify()` then `ScanApiClient.recordScan()`
- Exceptions from `TicketVerifier` map directly to `ScanResult` subtypes
- Brief loading state emitted while network call is in flight (scanner screen shows spinner overlay)

### `app/build.gradle.kts` (updated)
- Add `com.nimbusds:nimbus-jose-jwt` — standard Java JWT/JWS library with Ed25519 support (~200KB AAR)

## Error Handling

| Situation | Result shown |
|---|---|
| Malformed QR / not a JWT | FakeTicket |
| Bad signature | FakeTicket |
| Valid signature but expired | Expired |
| Valid but wrong event | WrongEntrance |
| Valid, not used — server 200 | Valid |
| Valid, already used — server 409 | AlreadyUsed |
| Network error | Toast "No connection", stay on Scanner |
| Server 5xx | Toast "Server error, try again", stay on Scanner |

## Testing

- `TicketVerifier` unit tests: valid JWT, bad signature, expired, wrong event, malformed string — all testable on JVM with no Android context
- `ScanApiClient` unit tests: mock HTTP responses for 200, 409, timeout
- `ScannerViewModel` integration tests: verify correct `ScanResult` emitted for each path
