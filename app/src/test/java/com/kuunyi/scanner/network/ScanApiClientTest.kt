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
