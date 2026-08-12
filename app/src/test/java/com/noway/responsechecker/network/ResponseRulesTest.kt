package com.noway.responsechecker.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseRulesTest {
    @Test fun normalizesBareDomainToHttps() {
        assertEquals("https://example.com", ResponseRules.normalizeTarget("example.com"))
    }

    @Test fun classifiesCoreResponseGroups() {
        assertEquals(ResultClass.SUCCESS, ResponseRules.classify(200, false, null))
        assertEquals(ResultClass.REDIRECTED, ResponseRules.classify(301, true, null))
        assertEquals(ResultClass.RESPONDED, ResponseRules.classify(403, false, null))
        assertEquals(ResultClass.FAILED, ResponseRules.classify(null, false, "timeout"))
    }

    @Test fun detectsCloudflareFromHeaders() {
        val hit = ResponseRules.detectCdn(mapOf("CF-Ray" to listOf("abc"), "Server" to listOf("cloudflare")))
        assertEquals("Cloudflare", hit?.provider)
        assertEquals("high", hit?.confidence)
    }

    @Test fun detectsCloudFrontFromHeaders() {
        val hit = ResponseRules.detectCdn(mapOf("X-Amz-Cf-Id" to listOf("id"), "X-Amz-Cf-Pop" to listOf("LHR")))
        assertEquals("Amazon CloudFront", hit?.provider)
        assertTrue(hit?.evidence?.size == 2)
    }
}
