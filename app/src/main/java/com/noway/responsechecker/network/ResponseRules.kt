package com.noway.responsechecker.network

import java.net.URI

object ResponseRules {
    fun normalizeTarget(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Target is empty" }
        val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
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

    fun detectCdn(headers: Map<String, List<String>>): CdnFinding? {
        val flat = headers.mapKeys { it.key.lowercase() }
        fun values(name: String) = flat[name]?.joinToString(", ").orEmpty()
        val cloudflare = mutableListOf<String>()
        if (flat.containsKey("cf-ray")) cloudflare += "CF-Ray"
        if (flat.containsKey("cf-cache-status")) cloudflare += "CF-Cache-Status"
        if (values("server").contains("cloudflare", true)) cloudflare += "Server: cloudflare"
        if (cloudflare.isNotEmpty()) return CdnFinding("Cloudflare", if (cloudflare.size >= 2) "high" else "medium", cloudflare)

        val cloudFront = mutableListOf<String>()
        if (flat.containsKey("x-amz-cf-id")) cloudFront += "X-Amz-Cf-Id"
        if (flat.containsKey("x-amz-cf-pop")) cloudFront += "X-Amz-Cf-Pop"
        if (values("x-cache").contains("cloudfront", true)) cloudFront += "X-Cache contains CloudFront"
        if (values("via").contains("cloudfront", true)) cloudFront += "Via contains CloudFront"
        if (values("server").contains("cloudfront", true)) cloudFront += "Server: CloudFront"
        if (cloudFront.isNotEmpty()) return CdnFinding("Amazon CloudFront", if (cloudFront.size >= 2) "high" else "medium", cloudFront)

        val fastly = mutableListOf<String>()
        if (flat.containsKey("x-served-by")) fastly += "X-Served-By"
        if (flat.containsKey("x-fastly-request-id")) fastly += "X-Fastly-Request-ID"
        if (fastly.isNotEmpty()) return CdnFinding("Fastly", "medium", fastly)

        val akamai = mutableListOf<String>()
        if (flat.keys.any { it.startsWith("akamai-") }) akamai += "Akamai response header"
        if (flat.containsKey("x-akamai-transformed")) akamai += "X-Akamai-Transformed"
        return if (akamai.isNotEmpty()) CdnFinding("Akamai", "medium", akamai) else null
    }
}
