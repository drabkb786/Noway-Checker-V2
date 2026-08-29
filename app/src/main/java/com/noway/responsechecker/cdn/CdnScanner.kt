package com.noway.responsechecker.cdn

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

class CdnScanner {
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val genericHeaders = setOf("x-cache", "x-served-by", "x-cache-hits", "via", "server")

    fun normalizeHost(raw: String): String? {
        val trimmed = raw.trim().removePrefix("[").removeSuffix("]").trim()
        if (trimmed.isBlank()) return null

        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val host = runCatching { URI(candidate).host }.getOrNull()
            ?: candidate.substringAfter("://").substringBefore('/').substringBefore(':')
        if (host.isBlank() || host.contains(' ')) return null

        return runCatching {
            IDN.toASCII(host.trimEnd('.')).lowercase(Locale.US)
        }.getOrNull()?.takeIf { it.contains('.') || it == "localhost" }
    }

    fun scan(raw: String): CdnResult {
        val start = System.nanoTime()
        val host = normalizeHost(raw)
            ?: return CdnResult(raw, raw.trim(), error = "Invalid domain or URL")

        val ips = resolveIps(host)
        val cnameChain = resolveCnameChain(host)
        val cname = cnameChain.lastOrNull()
        val httpProbe = probeHttp(host)
        val detection = detect(host, cnameChain, httpProbe.headers, httpProbe.finalHost)
        val elapsed = (System.nanoTime() - start) / 1_000_000

        val networkError = if (ips.isEmpty() && httpProbe.error != null && cnameChain.isEmpty()) {
            httpProbe.error
        } else null

        return CdnResult(
            input = raw,
            host = host,
            provider = detection.provider,
            confidence = detection.confidence,
            httpCode = httpProbe.code,
            scheme = httpProbe.scheme,
            cname = cname,
            ips = ips,
            evidence = detection.evidence,
            error = networkError,
            durationMs = elapsed
        )
    }

    private fun resolveIps(host: String): List<String> = runCatching {
        InetAddress.getAllByName(host)
            .mapNotNull { it.hostAddress }
            .distinct()
            .take(8)
    }.getOrDefault(emptyList())

    private fun resolveCnameChain(host: String): List<String> {
        val chain = mutableListOf<String>()
        var current = host
        for (i in 0 until 5) {
            val next = queryCname(current) ?: break
            val normalized = next.trimEnd('.').lowercase(Locale.US)
            if (normalized == current || normalized in chain) break
            chain += normalized
            current = normalized
        }
        return chain
    }

    private fun queryCname(host: String): String? {
        val encoded = URLEncoder.encode(host, StandardCharsets.UTF_8.toString())
        val google = "https://dns.google/resolve?name=$encoded&type=CNAME"
        queryDoh(google, mapOf("Accept" to "application/dns-json"))?.let { return it }

        val cloudflare = "https://cloudflare-dns.com/dns-query?name=$encoded&type=CNAME"
        return queryDoh(cloudflare, mapOf("Accept" to "application/dns-json"))
    }

    private fun queryDoh(url: String, headers: Map<String, String>): String? = runCatching {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body.string()
            val json = JSONObject(body)
            val answers = json.optJSONArray("Answer") ?: return@use null
            for (i in 0 until answers.length()) {
                val item = answers.optJSONObject(i) ?: continue
                if (item.optInt("type") == 5) return@use item.optString("data").takeIf { it.isNotBlank() }
            }
            null
        }
    }.getOrNull()

    private data class HttpProbe(
        val code: Int? = null,
        val scheme: String? = null,
        val finalHost: String? = null,
        val headers: Map<String, List<String>> = emptyMap(),
        val error: String? = null
    )

    private fun probeHttp(host: String): HttpProbe {
        val https = executeProbe("https://$host")
        if (https.code != null) return https
        val http = executeProbe("http://$host")
        return if (http.code != null) http else HttpProbe(error = https.error ?: http.error ?: "Connection failed")
    }

    private fun executeProbe(url: String): HttpProbe {
        val head = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "NOWAY-CDN-Finder-R5/1.0")
            .header("Accept", "*/*")
            .build()

        val first = runCatching {
            client.newCall(head).execute().use { response ->
                toProbe(response.code, response.request.url.scheme, response.request.url.host, response.headers.toMultimap())
            }
        }.getOrElse { HttpProbe(error = readableError(it)) }

        if (first.code != null && first.code !in setOf(400, 403, 405, 406, 501)) return first

        val get = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "NOWAY-CDN-Finder-R5/1.0")
            .header("Accept", "text/html,*/*;q=0.8")
            .header("Range", "bytes=0-1023")
            .build()

        return runCatching {
            client.newCall(get).execute().use { response ->
                toProbe(response.code, response.request.url.scheme, response.request.url.host, response.headers.toMultimap())
            }
        }.getOrElse { first.takeIf { it.code != null } ?: HttpProbe(error = readableError(it)) }
    }

    private fun toProbe(code: Int, scheme: String, finalHost: String, headers: Map<String, List<String>>): HttpProbe {
        return HttpProbe(
            code = code,
            scheme = scheme,
            finalHost = finalHost,
            headers = headers.mapKeys { it.key.lowercase(Locale.US) }
        )
    }

    private fun readableError(t: Throwable): String {
        val name = t::class.java.simpleName.replace("Exception", "")
        val message = t.message?.take(90)?.replace('\n', ' ')
        return if (message.isNullOrBlank()) name else "$name: $message"
    }

    private data class Detection(val provider: String, val confidence: Int, val evidence: List<String>)

    private fun detect(
        host: String,
        cnameChain: List<String>,
        headers: Map<String, List<String>>,
        finalHost: String?
    ): Detection {
        val flattenedValues = headers.flatMap { (name, values) -> values.map { "$name:$it" } }
        val serverValue = headers["server"].orEmpty().joinToString(" ").lowercase(Locale.US)
        val redirectHost = finalHost?.lowercase(Locale.US)

        var bestProvider = "Unknown"
        var bestScore = 0
        var bestEvidence = emptyList<String>()

        CdnSignatures.all.forEach { sig ->
            var score = 0
            val evidence = linkedSetOf<String>()

            val cnameHit = cnameChain.firstOrNull { cname -> sig.cnameTokens.any { token -> cname.contains(token, true) } }
            if (cnameHit != null) {
                score += 65
                evidence += "CNAME → $cnameHit"
            }

            if (sig.cnameTokens.any { token -> host.contains(token, true) }) {
                score += 55
                evidence += "Host matches ${sig.provider} edge domain"
            }

            if (redirectHost != null && sig.cnameTokens.any { token -> redirectHost.contains(token, true) }) {
                score += 45
                evidence += "Redirect edge → $redirectHost"
            }

            sig.headerNames.forEach { headerName ->
                val key = headerName.lowercase(Locale.US)
                if (headers.containsKey(key)) {
                    score += if (key in genericHeaders) 12 else 42
                    evidence += "Header: $key"
                }
            }

            sig.headerValueTokens.forEach { token ->
                val hit = flattenedValues.firstOrNull { it.contains(token, ignoreCase = true) }
                if (hit != null) {
                    score += 28
                    evidence += "Header value: ${token.take(32)}"
                }
            }

            sig.serverTokens.forEach { token ->
                if (serverValue.contains(token, ignoreCase = true)) {
                    score += 38
                    evidence += "Server: ${headers["server"].orEmpty().joinToString(" ").take(50)}"
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestProvider = sig.provider
                bestEvidence = evidence.toList()
            }
        }

        if (bestScore < 35) return Detection("Unknown", 0, emptyList())
        val confidence = min(99, when {
            bestScore >= 100 -> 98
            bestScore >= 75 -> 92
            bestScore >= 60 -> 86
            bestScore >= 45 -> 76
            else -> 62
        })
        return Detection(bestProvider, confidence, bestEvidence.take(5))
    }
}
