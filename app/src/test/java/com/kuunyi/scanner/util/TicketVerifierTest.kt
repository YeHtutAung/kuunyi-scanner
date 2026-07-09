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
