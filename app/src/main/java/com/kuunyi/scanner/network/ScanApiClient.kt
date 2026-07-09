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
    private fun String.escapeJson() = replace("\\", "\\\\").replace("\"", "\\\"")

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
                val body = """{"jti":"${jti.escapeJson()}","eid":"${eid.escapeJson()}","gate":"${gate.escapeJson()}"}"""
                conn.outputStream.use { it.write(body.toByteArray()) }
                when (conn.responseCode) {
                    200 -> ScanApiResult.Ok
                    409 -> {
                        // HttpURLConnection puts 4xx bodies on errorStream (not inputStream).
                        // MockWebServer serves them on inputStream regardless, so this path
                        // is tested via MockWebServer abstraction, not the real stream.
                        val json = (conn.errorStream ?: conn.inputStream).bufferedReader().readText()
                        val time = Regex(""""firstScanTime"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: ""
                        val g = Regex(""""firstScanGate"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: ""
                        ScanApiResult.AlreadyUsed(time, g)
                    }
                    400, 404 -> ScanApiResult.NotFound
                    401, 403 -> ScanApiResult.AuthError
                    else -> ScanApiResult.ServerError
                }
            } catch (e: Exception) {
                // Rethrow CancellationException to preserve coroutine cancellation.
                // Covers IOException, SocketTimeoutException, SSLHandshakeException, etc.
                if (e is kotlinx.coroutines.CancellationException) throw e
                ScanApiResult.NetworkError
            }
        }
}
