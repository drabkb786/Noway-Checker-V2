package com.noway.responsechecker.network

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ResponseFormatter {
    fun raw(result: CheckResult): String = buildString {
        appendLine("TARGET --> ${result.finalUrl}")
        result.ipAddresses.forEach { appendLine("IP: $it") }
        result.selectedIp?.let { appendLine("Connected-IP: $it${result.selectedPort?.let { p -> ":$p" } ?: ""}") }
        result.tls?.cipherSuite?.let { appendLine("CipherSuite: $it") }
        result.tls?.protocol?.let { appendLine("Protocol: $it") }
        result.tls?.peerPrincipal?.let { appendLine("PeerPrincipal: $it") }
        result.http?.statusLine?.let { appendLine(it) }
        result.headerLines.forEach { appendLine(it) }

        val timing = result.timing
        val http = result.http
        if (timing?.receivedAtMillis != null || timing?.sentAtMillis != null || http?.selectedProtocol != null) {
            appendLine("---------------- DERIVED TRANSPORT DIAGNOSTICS ----------------")
            timing?.receivedAtMillis?.let { appendLine("X-Android-Received-Millis: $it") }
            http?.responseSource?.let { appendLine("X-Android-Response-Source: $it") }
            http?.selectedProtocol?.let { appendLine("X-Android-Selected-Protocol: $it") }
            timing?.sentAtMillis?.let { appendLine("X-Android-Sent-Millis: $it") }
        }

        appendLine("--------------------------- CDN ---------------------------")
        result.cdn?.let { cdn ->
            appendLine("Provider: ${cdn.provider}")
            appendLine("Confidence: ${cdn.confidence}")
            cdn.pop?.let { appendLine("POP: $it") }
            cdn.cacheStatus?.let { appendLine("Cache: $it") }
            cdn.requestId?.let { appendLine("Request-ID: $it") }
            cdn.evidence.forEach { appendLine("Evidence: $it") }
        } ?: appendLine("No supported CDN fingerprint detected")
        appendLine("------------------------- END CDN -------------------------")
    }

    fun time(millis: Long?): String = millis?.let {
        SimpleDateFormat("dd MMM yyyy HH:mm:ss.SSS", Locale.getDefault()).format(Date(it))
    } ?: "—"
}
