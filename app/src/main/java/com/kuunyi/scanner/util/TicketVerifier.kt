package com.kuunyi.scanner.util

import com.kuunyi.scanner.data.*
import com.nimbusds.jose.util.Base64URL
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
