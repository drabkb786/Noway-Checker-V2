package com.noway.responsechecker.cdn

data class CdnResult(
    val input: String,
    val host: String,
    val provider: String = "Unknown",
    val confidence: Int = 0,
    val httpCode: Int? = null,
    val scheme: String? = null,
    val cname: String? = null,
    val ips: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val error: String? = null,
    val durationMs: Long = 0L
) {
    val detected: Boolean get() = provider != "Unknown" && error == null
    val statusLabel: String get() = when {
        error != null -> "ERROR"
        detected -> "DETECTED"
        else -> "UNKNOWN"
    }

    fun compactLine(): String {
        val code = httpCode?.toString() ?: "-"
        val cnameValue = cname ?: "-"
        val ipValue = if (ips.isEmpty()) "-" else ips.joinToString(",")
        val evidenceValue = if (evidence.isEmpty()) "-" else evidence.joinToString("; ")
        return "$host | $provider | ${confidence}% | HTTP $code | CNAME $cnameValue | IP $ipValue | $evidenceValue"
    }
}
