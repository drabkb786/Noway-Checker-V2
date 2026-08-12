package com.noway.responsechecker.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseRulesTest {
    @Test fun normalizesBareDomainToHttps() {
        assertEquals("https://example.com", ResponseRules.normalizeTarget("example.com"))
    }

    @Test fun normalizesBareIpv6ToHttps() {
        assertEquals("https://[2001:db8::1]", ResponseRules.normalizeTarget("2001:db8::1"))
    }

    @Test fun classifiesCoreResponseGroups() {
        assertEquals(ResultClass.SUCCESS, ResponseRules.classify(200, false, null))
        assertEquals(ResultClass.REDIRECTED, ResponseRules.classify(301, true, null))
        assertEquals(ResultClass.RESPONDED, ResponseRules.classify(403, false, null))
        assertEquals(ResultClass.FAILED, ResponseRules.classify(null, false, "timeout"))
    }

    @Test fun labelsProtocolsForHumanDisplay() {
        assertEquals("HTTP/1.1", ResponseRules.protocolLabel("http/1.1"))
        assertEquals("HTTP/2", ResponseRules.protocolLabel("h2"))
    }

    @Test fun detectsCloudflareWithPopAndCacheStatus() {
        val hit = ResponseRules.detectCdn(
            mapOf(
                "CF-Ray" to listOf("a2984184b984f462-LHE"),
                "CF-Cache-Status" to listOf("DYNAMIC"),
                "Server" to listOf("cloudflare")
            )
        )
        assertEquals("Cloudflare", hit?.provider)
        assertEquals("high", hit?.confidence)
        assertEquals("LHE", hit?.pop)
        assertEquals("DYNAMIC", hit?.cacheStatus)
    }

    @Test fun detectsCloudFrontWithPopAndRequestId() {
        val hit = ResponseRules.detectCdn(
            mapOf(
                "X-Amz-Cf-Id" to listOf("request-id"),
                "X-Amz-Cf-Pop" to listOf("LHR61-P1"),
                "X-Cache" to listOf("Hit from cloudfront")
            )
        )
        assertEquals("Amazon CloudFront", hit?.provider)
        assertEquals("LHR61-P1", hit?.pop)
        assertEquals("request-id", hit?.requestId)
        assertTrue(hit?.evidence?.size ?: 0 >= 2)
    }
}
