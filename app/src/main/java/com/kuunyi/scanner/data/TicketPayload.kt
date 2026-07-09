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
