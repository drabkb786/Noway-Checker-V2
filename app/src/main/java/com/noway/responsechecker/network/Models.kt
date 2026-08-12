package com.noway.responsechecker.network

enum class ResultClass { SUCCESS, REDIRECTED, RESPONDED, FAILED }

data class RedirectHop(
    val code: Int,
    val from: String,
    val to: String
)

data class TlsInfo(
    val protocol: String? = null,
    val cipherSuite: String? = null,
    val subject: String? = null,
    val issuer: String? = null,
    val validUntil: Long? = null
)

data class CdnFinding(
    val provider: String,
    val confidence: String,
    val evidence: List<String>
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
    val headers: Map<String, List<String>> = emptyMap(),
    val bodyPreview: String = "",
    val redirectChain: List<RedirectHop> = emptyList(),
    val tls: TlsInfo? = null,
    val cdn: CdnFinding? = null,
    val error: String? = null,
    val checkedAt: Long = System.currentTimeMillis()
)
