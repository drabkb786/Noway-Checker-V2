package com.noway.responsechecker.network

enum class ResultClass { SUCCESS, REDIRECTED, RESPONDED, FAILED }

data class RedirectHop(
    val code: Int,
    val from: String,
    val to: String
)

data class TimingInfo(
    val dnsMs: Long? = null,
    val connectMs: Long? = null,
    val tlsMs: Long? = null,
    val requestHeadersMs: Long? = null,
    val serverWaitMs: Long? = null,
    val totalMs: Long = 0,
    val sentAtMillis: Long? = null,
    val receivedAtMillis: Long? = null
)

data class NetworkInfo(
    val transport: String = "Unknown",
    val carrier: String? = null,
    val metered: Boolean? = null,
    val validated: Boolean? = null,
    val vpn: Boolean = false,
    val dnsServers: List<String> = emptyList(),
    val systemProxy: String? = null
)

data class HttpInfo(
    val protocol: String? = null,
    val statusLine: String? = null,
    val responseSource: String? = null,
    val selectedProtocol: String? = null,
    val contentType: String? = null,
    val contentLength: Long? = null,
    val connectionProxy: String? = null
)

data class TlsInfo(
    val protocol: String? = null,
    val cipherSuite: String? = null,
    val peerPrincipal: String? = null,
    val subject: String? = null,
    val issuer: String? = null,
    val subjectAltNames: List<String> = emptyList(),
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val certificateSha256: String? = null
)

data class CdnFinding(
    val provider: String,
    val confidence: String,
    val evidence: List<String>,
    val pop: String? = null,
    val requestId: String? = null,
    val cacheStatus: String? = null
)

data class CheckResult(
    val target: String,
    val normalizedUrl: String,
    val finalUrl: String,
    val method: String,
    val statusCode: Int? = null,
    val statusMessage: String? = null,
    val resultClass: ResultClass,
    val durationMs: Long,
    val dnsMs: Long? = null,
    val ipAddresses: List<String> = emptyList(),
    val selectedIp: String? = null,
    val selectedPort: Int? = null,
    val network: NetworkInfo? = null,
    val timing: TimingInfo? = null,
    val http: HttpInfo? = null,
    val requestHeaders: Map<String, List<String>> = emptyMap(),
    val headers: Map<String, List<String>> = emptyMap(),
    val headerLines: List<String> = emptyList(),
    val bodyPreview: String = "",
    val redirectChain: List<RedirectHop> = emptyList(),
    val tls: TlsInfo? = null,
    val cdn: CdnFinding? = null,
    val error: String? = null,
    val checkedAt: Long = System.currentTimeMillis()
)
