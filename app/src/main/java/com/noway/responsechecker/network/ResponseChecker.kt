package com.noway.responsechecker.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.URI
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

class ResponseChecker {
    fun check(
        rawTarget: String,
        method: String = "GET",
        customHeaders: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
        timeoutSeconds: Long = 12,
        body: String = ""
    ): CheckResult {
        val started = System.nanoTime()
        var normalized = rawTarget.trim()
        return try {
            normalized = ResponseRules.normalizeTarget(rawTarget)
            val host = URI(normalized).host
            val dnsStart = System.nanoTime()
            val ips = runCatching { InetAddress.getAllByName(host).map { it.hostAddress.orEmpty() }.distinct() }.getOrDefault(emptyList())
            val dnsMs = elapsedMs(dnsStart)

            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .build()

            val requestBuilder = Request.Builder().url(normalized)
            customHeaders.forEach { (name, value) -> if (name.isNotBlank()) requestBuilder.header(name.trim(), value.trim()) }
            val verb = method.uppercase()
            when (verb) {
                "GET" -> requestBuilder.get()
                "HEAD" -> requestBuilder.head()
                "POST" -> requestBuilder.post(body.toRequestBody())
                "PUT" -> requestBuilder.put(body.toRequestBody())
                "DELETE" -> if (body.isBlank()) requestBuilder.delete() else requestBuilder.delete(body.toRequestBody())
                "OPTIONS" -> requestBuilder.method("OPTIONS", null)
                else -> requestBuilder.method(verb, if (body.isBlank()) null else body.toRequestBody())
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val hops = buildRedirectChain(response)
                val headerMap = response.headers.toMultimap()
                val preview = if (verb == "HEAD") "" else response.body?.source()?.let { source ->
                    source.request(64 * 1024L)
                    source.buffer.clone().readUtf8(minOf(source.buffer.size, 64 * 1024L))
                }.orEmpty()
                val handshake = response.handshake
                val cert = handshake?.peerCertificates?.firstOrNull() as? X509Certificate
                val tls = handshake?.let {
                    TlsInfo(
                        protocol = it.tlsVersion.javaName,
                        cipherSuite = it.cipherSuite.javaName,
                        subject = cert?.subjectX500Principal?.name,
                        issuer = cert?.issuerX500Principal?.name,
                        validUntil = cert?.notAfter?.time
                    )
                }
                CheckResult(
                    target = rawTarget,
                    normalizedUrl = normalized,
                    finalUrl = finalUrl,
                    method = verb,
                    statusCode = response.code,
                    statusMessage = response.message,
                    resultClass = ResponseRules.classify(response.code, hops.isNotEmpty(), null),
                    durationMs = elapsedMs(started),
                    dnsMs = dnsMs,
                    ipAddresses = ips,
                    headers = headerMap,
                    bodyPreview = preview,
                    redirectChain = hops,
                    tls = tls,
                    cdn = ResponseRules.detectCdn(headerMap)
                )
            }
        } catch (t: Throwable) {
            CheckResult(
                target = rawTarget,
                normalizedUrl = normalized,
                finalUrl = normalized,
                method = method.uppercase(),
                resultClass = ResultClass.FAILED,
                durationMs = elapsedMs(started),
                error = "${t::class.simpleName}: ${t.message ?: "Request failed"}"
            )
        }
    }

    private fun buildRedirectChain(response: okhttp3.Response): List<RedirectHop> {
        val chain = mutableListOf<okhttp3.Response>()
        var p = response.priorResponse
        while (p != null) {
            chain += p
            p = p.priorResponse
        }
        chain.reverse()
        if (chain.isEmpty()) return emptyList()
        return chain.mapIndexedNotNull { index, item ->
            val from = item.request.url.toString()
            val to = chain.getOrNull(index + 1)?.request?.url?.toString() ?: response.request.url.toString()
            RedirectHop(item.code, from, to)
        }
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000L
}
