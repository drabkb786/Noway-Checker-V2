package com.noway.responsechecker.network

import java.net.URI

object ResponseRules {
    fun normalizeTarget(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Target is empty" }
        val withScheme = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.count { it == ':' } >= 2 && !trimmed.startsWith("[") && !trimmed.contains('/') -> "https://[$trimmed]"
            else -> "https://$trimmed"
        }
        val uri = URI(withScheme)
        require(!uri.host.isNullOrBlank()) { "Invalid URL or host" }
        return uri.toString()
    }

    fun classify(status: Int?, redirected: Boolean, error: String?): ResultClass = when {
        error != null || status == null -> ResultClass.FAILED
        redirected || status in 300..399 -> ResultClass.REDIRECTED
        status in 200..299 -> ResultClass.SUCCESS
        else -> ResultClass.RESPONDED
    }

    fun protocolLabel(raw: String?): String? = when (raw?.lowercase()) {
        null -> null
        "http/1.0" -> "HTTP/1.0"
        "http/1.1" -> "HTTP/1.1"
        "h2", "h2_prior_knowledge" -> "HTTP/2"
        "h3", "quic" -> "HTTP/3"
        else -> raw.uppercase()
    }

    fun detectCdn(headers: Map<String, List<String>>): CdnFinding? {
        val flat = headers.mapKeys { it.key.lowercase() }
        fun values(name: String) = flat[name]?.joinToString(", ").orEmpty()
        fun one(name: String) = flat[name]?.firstOrNull()

        val cloudflare = mutableListOf<String>()
        val cfRay = one("cf-ray")
        val cfCache = one("cf-cache-status")
        if (cfRay != null) cloudflare += "CF-Ray"
        if (cfCache != null) cloudflare += "CF-Cache-Status"
        if (values("server").contains("cloudflare", true)) cloudflare += "Server: cloudflare"
        if (flat.containsKey("cf-request-id")) cloudflare += "CF-Request-ID"
        if (cloudflare.isNotEmpty()) {
            return CdnFinding(
                provider = "Cloudflare",
                confidence = if (cloudflare.size >= 2) "high" else "medium",
                evidence = cloudflare,
                pop = cfRay?.substringAfterLast('-', "")?.takeIf { it.isNotBlank() },
                requestId = cfRay ?: one("cf-request-id"),
                cacheStatus = cfCache
            )
        }

        val cloudFront = mutableListOf<String>()
        val cfId = one("x-amz-cf-id")
        val cfPop = one("x-amz-cf-pop")
        if (cfId != null) cloudFront += "X-Amz-Cf-Id"
        if (cfPop != null) cloudFront += "X-Amz-Cf-Pop"
        if (values("x-cache").contains("cloudfront", true)) cloudFront += "X-Cache contains CloudFront"
        if (values("via").contains("cloudfront", true)) cloudFront += "Via contains CloudFront"
        if (values("server").contains("cloudfront", true)) cloudFront += "Server: CloudFront"
        if (cloudFront.isNotEmpty()) {
            return CdnFinding(
                provider = "Amazon CloudFront",
                confidence = if (cloudFront.size >= 2) "high" else "medium",
                evidence = cloudFront,
                pop = cfPop,
                requestId = cfId,
                cacheStatus = one("x-cache")
            )
        }

        val fastly = mutableListOf<String>()
        if (flat.containsKey("x-served-by")) fastly += "X-Served-By"
        if (flat.containsKey("x-fastly-request-id")) fastly += "X-Fastly-Request-ID"
        if (values("via").contains("varnish", true)) fastly += "Via: varnish"
        if (fastly.isNotEmpty()) {
            return CdnFinding(
                provider = "Fastly",
                confidence = if (fastly.size >= 2) "high" else "medium",
                evidence = fastly,
                pop = one("x-served-by"),
                requestId = one("x-fastly-request-id"),
                cacheStatus = one("x-cache")
            )
        }

        val akamai = mutableListOf<String>()
        if (flat.keys.any { it.startsWith("akamai-") }) akamai += "Akamai response header"
        if (flat.containsKey("x-akamai-transformed")) akamai += "X-Akamai-Transformed"
        if (flat.containsKey("x-check-cacheable")) akamai += "X-Check-Cacheable"
        if (akamai.isNotEmpty()) {
            return CdnFinding(
                provider = "Akamai",
                confidence = if (akamai.size >= 2) "high" else "medium",
                evidence = akamai,
                cacheStatus = one("x-cache")
            )
        }

        return null
    }
}
