package com.kuunyi.scanner.data

sealed class ScanResult {
    data class Valid(
        val ticketId: String,
        val tier: String,
        val admits: Int = 1,
    ) : ScanResult()

    data class AlreadyUsed(
        val ticketId: String,
        val tier: String,
        val firstScanTime: String,
        val firstScanGate: String,
    ) : ScanResult()

    data class FakeTicket(
        val ticketId: String = "#TKT-UNKNOWN",
    ) : ScanResult()

    data class Expired(
        val ticketId: String,
        val tier: String,
        val validFor: String,
        val scannedAt: String,
    ) : ScanResult()

    data class WrongEntrance(
        val ticketId: String,
        val ticketTier: String,
        val gateTier: String,
    ) : ScanResult()
}
