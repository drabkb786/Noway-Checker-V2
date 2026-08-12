package com.noway.responsechecker.network

import android.content.Context
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

class ResponseChecker(private val context: Context? = null) {
    fun check(
        rawTarget: String,
        method: String = "GET",
        customHeaders: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
        timeoutSeconds: Long = 12,
        body: String = "",
        useSystemProxy: Boolean = false,
        captureBody: Boolean = true,
        bodyPreviewLimitBytes: Long = 64 * 1024L,
        cdnFinder: Boolean = true
    ): CheckResult {
        val started = System.nanoTime()
        var normalized = rawTarget.trim()
        val listener = TimingEventListener()
        val networkInfo = context?.let { runCatching { NetworkInspector.inspect(it) }.getOrNull() }
        return try {
            normalized = ResponseRules.normalizeTarget(rawTarget)
            val builder = OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .retryOnConnectionFailure(true)
                .eventListener(listener)
            if (!useSystemProxy) builder.proxy(Proxy.NO_PROXY)
            val client = builder.build()

            val requestBuilder = Request.Builder().url(normalized)
            customHeaders.forEach { (name, value) ->
                if (name.isNotBlank()) requestBuilder.header(name.trim(), value.trim())
            }
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
                val headerLines = response.headers.map { (name, value) -> "$name: $value" }
                val preview = if (!captureBody || verb == "HEAD") "" else response.body.source().let { source ->
                    val limit = bodyPreviewLimitBytes.coerceIn(0L, 256 * 1024L)
                    source.request(limit)
                    source.buffer.clone().readUtf8(minOf(source.buffer.size, limit))
                }
                val handshake = response.handshake
                val cert = handshake?.peerCertificates?.firstOrNull() as? X509Certificate
                val tls = handshake?.let {
                    TlsInfo(
                        protocol = it.tlsVersion.javaName,
                        cipherSuite = it.cipherSuite.javaName,
                        peerPrincipal = runCatching { it.peerPrincipal.name }.getOrNull(),
                        subject = cert?.subjectX500Principal?.name,
                        issuer = cert?.issuerX500Principal?.name,
                        subjectAltNames = certificateSans(cert),
                        validFrom = cert?.notBefore?.time,
                        validUntil = cert?.notAfter?.time,
                        certificateSha256 = cert?.encoded?.let(::sha256)
                    )
                }
                val rawProtocol = response.protocol.toString()
                val protocolLabel = ResponseRules.protocolLabel(rawProtocol)
                val contentLength = response.body.contentLength().takeIf { it >= 0L }
                val sourceLabel = when {
                    response.cacheResponse != null && response.networkResponse != null -> "CONDITIONAL_CACHE ${response.code}"
                    response.cacheResponse != null -> "CACHE ${response.code}"
                    else -> "NETWORK ${response.code}"
                }
                val http = HttpInfo(
                    protocol = protocolLabel,
                    statusLine = listOfNotNull(protocolLabel, response.code.toString(), response.message.takeIf { it.isNotBlank() }).joinToString(" "),
                    responseSource = sourceLabel,
                    selectedProtocol = rawProtocol,
                    contentType = response.body.contentType()?.toString() ?: response.header("Content-Type"),
                    contentLength = contentLength,
                    connectionProxy = listener.proxyDescription
                )
                val timing = listener.toTiming(elapsedMs(started))
                CheckResult(
                    target = rawTarget,
                    normalizedUrl = normalized,
                    finalUrl = finalUrl,
                    method = verb,
                    statusCode = response.code,
                    statusMessage = response.message,
                    resultClass = ResponseRules.classify(response.code, hops.isNotEmpty(), null),
                    durationMs = timing.totalMs,
                    dnsMs = timing.dnsMs,
                    ipAddresses = listener.resolvedAddresses.ifEmpty { listener.selectedIp?.let(::listOf).orEmpty() },
                    selectedIp = listener.selectedIp,
                    selectedPort = listener.selectedPort,
                    network = networkInfo,
                    timing = timing,
                    http = http,
                    requestHeaders = response.request.headers.toMultimap(),
                    headers = headerMap,
                    headerLines = headerLines,
                    bodyPreview = preview,
                    redirectChain = hops,
                    tls = tls,
                    cdn = if (cdnFinder) ResponseRules.detectCdn(headerMap) else null
                )
            }
        } catch (t: Throwable) {
            val timing = listener.toTiming(elapsedMs(started))
            CheckResult(
                target = rawTarget,
                normalizedUrl = normalized,
                finalUrl = normalized,
                method = method.uppercase(),
                resultClass = ResultClass.FAILED,
                durationMs = timing.totalMs,
                dnsMs = timing.dnsMs,
                ipAddresses = listener.resolvedAddresses,
                selectedIp = listener.selectedIp,
                selectedPort = listener.selectedPort,
                network = networkInfo,
                timing = timing,
                http = HttpInfo(connectionProxy = listener.proxyDescription),
                error = "${t::class.simpleName}: ${t.message ?: "Request failed"}"
            )
        }
    }

    private fun buildRedirectChain(response: Response): List<RedirectHop> {
        val chain = mutableListOf<Response>()
        var p = response.priorResponse
        while (p != null) {
            chain += p
            p = p.priorResponse
        }
        chain.reverse()
        if (chain.isEmpty()) return emptyList()
        return chain.mapIndexed { index, item ->
            val from = item.request.url.toString()
            val to = chain.getOrNull(index + 1)?.request?.url?.toString() ?: response.request.url.toString()
            RedirectHop(item.code, from, to)
        }
    }

    private fun certificateSans(cert: X509Certificate?): List<String> = runCatching {
        cert?.subjectAlternativeNames?.mapNotNull { row ->
            if (row != null && row.size > 1) row[1]?.toString() else null
        }?.distinct()?.take(24).orEmpty()
    }.getOrDefault(emptyList())

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString(":") { "%02X".format(it) }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000L
}

private class TimingEventListener : EventListener() {
    private var dnsStartNs: Long? = null
    private var dnsMs: Long? = null
    private var connectStartNs: Long? = null
    private var connectMs: Long? = null
    private var tlsStartNs: Long? = null
    private var tlsMs: Long? = null
    private var requestHeadersStartNs: Long? = null
    private var requestHeadersMs: Long? = null
    private var requestHeadersEndNs: Long? = null
    private var responseHeadersStartNs: Long? = null
    var sentAtMillis: Long? = null
        private set
    var receivedAtMillis: Long? = null
        private set
    var resolvedAddresses: List<String> = emptyList()
        private set
    var selectedIp: String? = null
        private set
    var selectedPort: Int? = null
        private set
    var proxyDescription: String? = null
        private set

    override fun dnsStart(call: Call, domainName: String) {
        dnsStartNs = System.nanoTime()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        dnsStartNs?.let { dnsMs = nsToMs(System.nanoTime() - it) }
        resolvedAddresses = inetAddressList.mapNotNull { it.hostAddress }.distinct()
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStartNs = System.nanoTime()
        selectedIp = inetSocketAddress.address?.hostAddress ?: inetSocketAddress.hostString
        selectedPort = inetSocketAddress.port
        proxyDescription = if (proxy == Proxy.NO_PROXY) "DIRECT" else "${proxy.type()} ${proxy.address()}"
    }

    override fun secureConnectStart(call: Call) {
        tlsStartNs = System.nanoTime()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        tlsStartNs?.let { tlsMs = nsToMs(System.nanoTime() - it) }
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        connectStartNs?.let { connectMs = nsToMs(System.nanoTime() - it) }
    }

    override fun requestHeadersStart(call: Call) {
        requestHeadersStartNs = System.nanoTime()
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        val now = System.nanoTime()
        requestHeadersEndNs = now
        requestHeadersStartNs?.let { requestHeadersMs = nsToMs(now - it) }
        sentAtMillis = System.currentTimeMillis()
    }

    override fun responseHeadersStart(call: Call) {
        responseHeadersStartNs = System.nanoTime()
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        receivedAtMillis = System.currentTimeMillis()
    }

    fun toTiming(totalMs: Long): TimingInfo {
        val wait = if (requestHeadersEndNs != null && responseHeadersStartNs != null) {
            nsToMs(responseHeadersStartNs!! - requestHeadersEndNs!!)
        } else null
        return TimingInfo(
            dnsMs = dnsMs,
            connectMs = connectMs,
            tlsMs = tlsMs,
            requestHeadersMs = requestHeadersMs,
            serverWaitMs = wait,
            totalMs = totalMs,
            sentAtMillis = sentAtMillis,
            receivedAtMillis = receivedAtMillis
        )
    }

    private fun nsToMs(value: Long): Long = value.coerceAtLeast(0L) / 1_000_000L
}
