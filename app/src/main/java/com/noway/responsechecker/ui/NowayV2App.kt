package com.noway.responsechecker.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.noway.responsechecker.data.BulkRun
import com.noway.responsechecker.data.HistoryDatabase
import com.noway.responsechecker.data.HistoryRow
import com.noway.responsechecker.network.CheckResult
import com.noway.responsechecker.network.NetworkInfo
import com.noway.responsechecker.network.NetworkInspector
import com.noway.responsechecker.network.ResponseChecker
import com.noway.responsechecker.network.ResponseFormatter
import com.noway.responsechecker.network.ResultClass
import com.noway.responsechecker.worker.BulkCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val UixBg = Color(0xFF080B11)
private val UixSurface = Color(0xFF101620)
private val UixSurface2 = Color(0xFF151D29)
private val UixSurface3 = Color(0xFF1A2533)
private val UixCyan = Color(0xFF55E7D2)
private val UixBlue = Color(0xFF6EA8FF)
private val UixPurple = Color(0xFFA990FF)
private val UixAmber = Color(0xFFFFC766)
private val UixRed = Color(0xFFFF7185)
private val UixText = Color(0xFFF3F7FA)
private val UixMuted = Color(0xFF91A0AF)

private val uixScheme = darkColorScheme(
    primary = UixCyan,
    secondary = UixBlue,
    tertiary = UixPurple,
    background = UixBg,
    surface = UixSurface,
    surfaceVariant = UixSurface2,
    onPrimary = Color(0xFF00201B),
    onBackground = UixText,
    onSurface = UixText,
    error = UixRed
)

@Composable
fun NowayV2App() {
    MaterialTheme(colorScheme = uixScheme) {
        var unlocked by rememberSaveable { mutableStateOf(false) }
        if (unlocked) UixShell() else UixLogin { unlocked = true }
    }
}

@Composable
private fun UixLogin(onSuccess: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF0B1720), UixBg, Color(0xFF100E1C)))
        ).padding(22.dp)
    ) {
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().widthIn(max = 480.dp),
            color = UixSurface.copy(alpha = .96f),
            shape = RoundedCornerShape(34.dp),
            tonalElevation = 2.dp
        ) {
            Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(78.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(UixCyan, UixBlue, UixPurple))),
                    contentAlignment = Alignment.Center
                ) { Text("N", color = Color(0xFF061018), fontSize = 38.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(18.dp))
                Text("NOWAY", fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                Text("RESPONSE CHECKER", color = UixCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
                Spacer(Modifier.height(8.dp))
                BadgePill("V2 • UIX 6", UixPurple)
                Spacer(Modifier.height(8.dp))
                Text("Owner • Abdul Basit", color = UixMuted, fontSize = 12.sp)
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Access password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = error,
                    shape = RoundedCornerShape(18.dp)
                )
                if (error) Text("Incorrect password", color = UixRed, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (sha256(password) == "ddc791927b7836283b618b8c69107cdee841d28f47feffa5f91a5e5318228997") onSuccess() else error = true
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("ENTER UIX 6", fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
            }
        }
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

private enum class UixTab(val label: String, val symbol: String) {
    HOME("Pulse", "◉"), CHECK("Checker", "↗"), BULK("Bulk", "≋"), HISTORY("History", "⌁")
}

@Composable
private fun UixShell() {
    val context = LocalContext.current
    val db = remember { HistoryDatabase(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { db.close() } }
    var tab by rememberSaveable { mutableStateOf(UixTab.CHECK) }
    var refresh by remember { mutableIntStateOf(0) }
    var network by remember { mutableStateOf<NetworkInfo?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            network = withContext(Dispatchers.IO) { runCatching { NetworkInspector.inspect(context) }.getOrNull() }
            delay(3000)
        }
    }

    Scaffold(
        containerColor = UixBg,
        topBar = {
            Surface(color = UixBg) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NOWAY", fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            Spacer(Modifier.width(8.dp))
                            BadgePill("UIX 6", UixPurple)
                        }
                        Text("Full response intelligence", color = UixMuted, fontSize = 11.sp)
                    }
                    NetworkBadge(network)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0A0F16)) {
                UixTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.symbol, fontSize = 19.sp) },
                        label = { Text(item.label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                UixTab.HOME -> UixDashboard(db, refresh)
                UixTab.CHECK -> UixChecker(db) { refresh++ }
                UixTab.BULK -> UixBulk(context, db) { refresh++ }
                UixTab.HISTORY -> UixHistory(context, db, refresh)
            }
        }
    }
}

@Composable
private fun NetworkBadge(info: NetworkInfo?) {
    val label = when {
        info == null -> "NETWORK"
        !info.carrier.isNullOrBlank() -> info.carrier
        else -> info.transport
    }
    Surface(color = UixCyan.copy(alpha = .10f), shape = RoundedCornerShape(100.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = UixCyan, fontSize = 9.sp)
            Spacer(Modifier.width(5.dp))
            Text(label ?: "NETWORK", color = UixCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun BadgePill(text: String, color: Color) {
    Surface(color = color.copy(alpha = .13f), shape = RoundedCornerShape(100.dp)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
    }
}

@Composable
private fun UixDashboard(db: HistoryDatabase, refresh: Int) {
    var counts by remember { mutableStateOf(emptyMap<ResultClass, Int>()) }
    var recent by remember { mutableStateOf(emptyList<HistoryRow>()) }
    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) {
            counts = db.counts()
            recent = db.recent(8)
        }
    }
    val total = counts.values.sum()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = UixSurface2,
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text("NETWORK PULSE", color = UixCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(total.toString(), fontSize = 44.sp, fontWeight = FontWeight.Black)
                    Text("stored response observations", color = UixMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("2xx", counts[ResultClass.SUCCESS] ?: 0, UixCyan, Modifier.weight(1f))
                        MiniStat("3xx", counts[ResultClass.REDIRECTED] ?: 0, UixPurple, Modifier.weight(1f))
                        MiniStat("4/5xx", counts[ResultClass.RESPONDED] ?: 0, UixAmber, Modifier.weight(1f))
                        MiniStat("Fail", counts[ResultClass.FAILED] ?: 0, UixRed, Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionLabel("RECENT SIGNALS") }
        if (recent.isEmpty()) item { EmptyPanel("No checks yet. Open Checker and probe a target.") }
        else items(recent) { HistoryLine(it) }
    }
}

@Composable
private fun MiniStat(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = UixBg.copy(alpha = .62f), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Text(value.toString(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, fontSize = 9.sp, color = UixMuted)
        }
    }
}

@Composable
private fun UixChecker(db: HistoryDatabase, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("GET") }
    var headersText by rememberSaveable { mutableStateOf("User-Agent: NOWAY-Response-Checker/2.0\nAccept: */*") }
    var body by rememberSaveable { mutableStateOf("") }
    var followRedirects by rememberSaveable { mutableStateOf(true) }
    var useProxy by rememberSaveable { mutableStateOf(false) }
    var showIpInfo by rememberSaveable { mutableStateOf(true) }
    var cdnFinder by rememberSaveable { mutableStateOf(true) }
    var captureBody by rememberSaveable { mutableStateOf(true) }
    var timeout by rememberSaveable { mutableLongStateOf(12L) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CheckResult?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(2.dp))
        Text("Response checker", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("HTTP • IP • TLS • redirects • CDN • timing", color = UixMuted, fontSize = 12.sp)

        Surface(color = UixSurface, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Host / IP / URL") },
                    placeholder = { Text("app.example.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("GET", "HEAD", "POST").forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(selected = useProxy, onClick = { useProxy = !useProxy }, label = { Text("Proxy") })
                    FilterChip(selected = showIpInfo, onClick = { showIpInfo = !showIpInfo }, label = { Text("IP Info") })
                    FilterChip(selected = cdnFinder, onClick = { cdnFinder = !cdnFinder }, label = { Text("CDN Finder") })
                    FilterChip(selected = captureBody, onClick = { captureBody = !captureBody }, label = { Text("Body") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Follow redirects", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Keep the full redirect chain", color = UixMuted, fontSize = 10.sp)
                    }
                    Switch(checked = followRedirects, onCheckedChange = { followRedirects = it })
                }
                Surface(
                    color = UixSurface2,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().clickable { advanced = !advanced }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Advanced request", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (advanced) "−" else "+", color = UixCyan, fontSize = 22.sp)
                    }
                }
                AnimatedVisibility(advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Timeout", color = UixMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(5L, 12L, 20L, 30L).forEach { t ->
                                FilterChip(selected = timeout == t, onClick = { timeout = t }, label = { Text("${t}s") })
                            }
                        }
                        OutlinedTextField(
                            value = headersText,
                            onValueChange = { headersText = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                            label = { Text("Custom headers • one per line") },
                            shape = RoundedCornerShape(16.dp),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        )
                        if (method == "POST") {
                            OutlinedTextField(
                                value = body,
                                onValueChange = { body = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                                label = { Text("Request body") },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        loading = true
                        scope.launch {
                            val checked = withContext(Dispatchers.IO) {
                                ResponseChecker(context.applicationContext).check(
                                    rawTarget = target,
                                    method = method,
                                    customHeaders = parseHeaders(headersText),
                                    followRedirects = followRedirects,
                                    timeoutSeconds = timeout,
                                    body = body,
                                    useSystemProxy = useProxy,
                                    captureBody = captureBody,
                                    cdnFinder = cdnFinder
                                )
                            }
                            withContext(Dispatchers.IO) { db.save(checked) }
                            result = checked
                            loading = false
                            onSaved()
                        }
                    },
                    enabled = !loading && target.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(19.dp)
                ) {
                    Text(if (loading) "CHECKING…" else "START RESPONSE CHECK", fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                }
            }
        }

        result?.let { DetailedResult(it, showIpInfo) }
        Spacer(Modifier.height(26.dp))
    }
}

private fun parseHeaders(text: String): Map<String, String> = text.lineSequence().mapNotNull { line ->
    val i = line.indexOf(':')
    if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
}.filter { it.first.isNotBlank() }.toMap()

@Composable
private fun DetailedResult(result: CheckResult, showIpInfo: Boolean) {
    val clipboard = LocalClipboardManager.current
    val color = resultColor(result.resultClass)
    val raw = remember(result) { ResponseFormatter.raw(result) }

    Surface(
        color = UixSurface2,
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(result.resultClass.name, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(result.statusCode?.toString() ?: "NO RESPONSE", fontSize = 38.sp, fontWeight = FontWeight.Black)
                    Text(result.http?.statusLine ?: result.error.orEmpty(), color = UixMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), horizontalAlignment = Alignment.End) {
                        Text("${result.durationMs} ms", color = color, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text(result.http?.protocol ?: "transport", color = UixMuted, fontSize = 9.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultMetric("CDN", result.cdn?.provider ?: "—", UixPurple, Modifier.weight(1f))
                ResultMetric("IP", result.selectedIp ?: result.ipAddresses.firstOrNull() ?: "—", UixBlue, Modifier.weight(1f))
                ResultMetric("TLS", result.tls?.protocol ?: "—", UixCyan, Modifier.weight(1f))
            }
        }
    }

    Spacer(Modifier.height(2.dp))

    DetailSection("Target & route", "ROUTE", true) {
        InfoRow("Target", result.normalizedUrl)
        if (result.finalUrl != result.normalizedUrl) InfoRow("Final URL", result.finalUrl)
        if (showIpInfo) {
            InfoRow("Connected IP", buildString {
                append(result.selectedIp ?: "—")
                result.selectedPort?.let { append(":$it") }
            })
            InfoRow("Resolved IPs", if (result.ipAddresses.isEmpty()) "—" else result.ipAddresses.joinToString("\n"))
        }
        result.network?.let { n ->
            InfoRow("Network", listOfNotNull(n.transport, n.carrier).distinct().joinToString(" • "))
            InfoRow("Validated / Metered", "${n.validated ?: "—"} / ${n.metered ?: "—"}")
            InfoRow("VPN", n.vpn.toString())
            InfoRow("DNS servers", if (n.dnsServers.isEmpty()) "—" else n.dnsServers.joinToString(" • "))
            InfoRow("System proxy", n.systemProxy ?: "None")
        }
        InfoRow("Connection proxy", result.http?.connectionProxy ?: "—")
    }

    DetailSection("HTTP response", "HTTP", true) {
        InfoRow("Status line", result.http?.statusLine ?: "—")
        InfoRow("Response source", result.http?.responseSource ?: "—")
        InfoRow("Selected protocol", result.http?.selectedProtocol ?: "—")
        InfoRow("Content type", result.http?.contentType ?: "—")
        InfoRow("Content length", result.http?.contentLength?.let(::formatBytes) ?: "Unknown / chunked")
        if (result.requestHeaders.isNotEmpty()) {
            Text("REQUEST HEADERS", color = UixMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            MonoBox(result.requestHeaders.entries.sortedBy { it.key.lowercase() }.joinToString("\n") { "${it.key}: ${it.value.joinToString(", ")}" })
        }
    }

    result.tls?.let { tls ->
        DetailSection("TLS & certificate", "TLS", true) {
            InfoRow("Protocol", tls.protocol ?: "—")
            InfoRow("CipherSuite", tls.cipherSuite ?: "—")
            InfoRow("PeerPrincipal", tls.peerPrincipal ?: "—")
            InfoRow("Subject", tls.subject ?: "—")
            InfoRow("Issuer", tls.issuer ?: "—")
            InfoRow("Valid from", ResponseFormatter.time(tls.validFrom))
            InfoRow("Valid until", ResponseFormatter.time(tls.validUntil))
            InfoRow("SHA-256 fingerprint", tls.certificateSha256 ?: "—")
            if (tls.subjectAltNames.isNotEmpty()) InfoRow("SANs", tls.subjectAltNames.joinToString("\n"))
        }
    }

    DetailSection("Timing breakdown", "TIME", false) {
        val t = result.timing
        InfoRow("DNS", t?.dnsMs?.let { "$it ms" } ?: "—")
        InfoRow("Connect", t?.connectMs?.let { "$it ms" } ?: "—")
        InfoRow("TLS handshake", t?.tlsMs?.let { "$it ms" } ?: "—")
        InfoRow("Request headers", t?.requestHeadersMs?.let { "$it ms" } ?: "—")
        InfoRow("Server wait", t?.serverWaitMs?.let { "$it ms" } ?: "—")
        InfoRow("Total", "${result.durationMs} ms")
        InfoRow("Sent", ResponseFormatter.time(t?.sentAtMillis))
        InfoRow("Received", ResponseFormatter.time(t?.receivedAtMillis))
    }

    if (result.redirectChain.isNotEmpty()) {
        DetailSection("Redirect chain", "${result.redirectChain.size} HOPS", true) {
            result.redirectChain.forEachIndexed { index, hop ->
                Surface(color = UixBg.copy(alpha = .6f), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${index + 1}. HTTP ${hop.code}", color = UixPurple, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text(hop.from, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        Text("↳ ${hop.to}", color = UixMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    DetailSection("CDN Finder", result.cdn?.provider?.uppercase() ?: "NO MATCH", true) {
        result.cdn?.let { cdn ->
            InfoRow("Provider", cdn.provider)
            InfoRow("Confidence", cdn.confidence)
            cdn.pop?.let { InfoRow("POP / Edge", it) }
            cdn.cacheStatus?.let { InfoRow("Cache status", it) }
            cdn.requestId?.let { InfoRow("Request / Ray ID", it) }
            InfoRow("Evidence", cdn.evidence.joinToString("\n"))
        } ?: Text("No Cloudflare, CloudFront, Fastly or Akamai response fingerprint detected.", color = UixMuted, fontSize = 11.sp)
    }

    DetailSection("Response headers", "${result.headerLines.size}", true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Original server response headers", color = UixMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(result.headerLines.joinToString("\n"))) }) { Text("COPY") }
        }
        MonoBox(if (result.headerLines.isEmpty()) "No response headers" else result.headerLines.joinToString("\n"))
    }

    DetailSection("Raw HTTP Custom-style output", "RAW", false) {
        Text("Server headers + clearly separated derived transport diagnostics", color = UixMuted, fontSize = 10.sp)
        TextButton(onClick = { clipboard.setText(AnnotatedString(raw)) }) { Text("COPY FULL RAW") }
        MonoBox(raw)
    }

    if (result.bodyPreview.isNotBlank()) {
        DetailSection("Body preview", "${result.bodyPreview.length} CHARS", false) {
            MonoBox(result.bodyPreview, maxLines = 45)
        }
    }

    result.error?.let {
        Surface(color = UixRed.copy(alpha = .10f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Text(it, Modifier.padding(14.dp), color = UixRed, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ResultMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = UixBg.copy(alpha = .58f), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = UixMuted, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Text(value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailSection(title: String, badge: String, initialExpanded: Boolean, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initialExpanded) }
    Surface(color = UixSurface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.weight(1f))
                BadgePill(badge, if (expanded) UixCyan else UixMuted)
                Spacer(Modifier.width(8.dp))
                Text(if (expanded) "−" else "+", color = UixMuted, fontSize = 19.sp)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 15.dp, end = 15.dp, bottom = 15.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label.uppercase(), color = UixMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        SelectionContainer { Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp) }
    }
}

@Composable
private fun MonoBox(text: String, maxLines: Int = Int.MAX_VALUE) {
    Surface(color = UixBg.copy(alpha = .70f), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                maxLines = maxLines,
                overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
            )
        }
    }
}

private fun resultColor(resultClass: ResultClass): Color = when (resultClass) {
    ResultClass.SUCCESS -> UixCyan
    ResultClass.REDIRECTED -> UixPurple
    ResultClass.RESPONDED -> UixAmber
    ResultClass.FAILED -> UixRed
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KiB".format(bytes / 1024.0)
    else -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun UixBulk(context: Context, db: HistoryDatabase, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("noway", Context.MODE_PRIVATE) }
    var input by rememberSaveable { mutableStateOf("") }
    var runId by rememberSaveable { mutableStateOf(prefs.getString("last_run", null)) }
    var run by remember { mutableStateOf<BulkRun?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            input = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
        }
    }

    LaunchedEffect(runId) {
        while (true) {
            runId?.let { run = withContext(Dispatchers.IO) { db.bulkRun(it) } }
            delay(750)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Bulk checker", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Persistent multi-target response queue", color = UixMuted, fontSize = 12.sp)
        Surface(color = UixSurface, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/plain", "text/csv", "application/json", "*/*")) }) { Text("IMPORT") }
                    Spacer(Modifier.width(10.dp))
                    Text("TXT / CSV / pasted lines", color = UixMuted, fontSize = 10.sp)
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 210.dp),
                    label = { Text("One domain / IP / URL per line") },
                    shape = RoundedCornerShape(18.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                )
                val count = remember(input) { parseTargets(input).size }
                Text("$count unique targets", color = if (count > 0) UixCyan else UixMuted, fontWeight = FontWeight.Bold)
                Button(
                    enabled = count > 0,
                    onClick = {
                        scope.launch {
                            val id = withContext(Dispatchers.IO) { db.createBulkRun(parseTargets(input)) }
                            runId = id
                            prefs.edit().putString("last_run", id).apply()
                            enqueueRun(context, id)
                            onChanged()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("START BULK CHECK", fontWeight = FontWeight.Black) }
                Text("Bulk mode skips response bodies for speed and stores status/CDN/route diagnostics in history.", color = UixMuted, fontSize = 10.sp)
            }
        }
        run?.let { r ->
            Surface(color = UixSurface2, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ACTIVE RUN • ${r.status}", color = UixCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("${r.completed} / ${r.total}", fontSize = 34.sp, fontWeight = FontWeight.Black)
                    LinearProgressIndicator(progress = { if (r.total == 0) 0f else r.completed.toFloat() / r.total }, modifier = Modifier.fillMaxWidth())
                    Text("${r.pending} pending • concurrency ${r.concurrency}", color = UixMuted, fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { db.setRunStatus(r.id, "PAUSED") }; run = db.bulkRun(r.id) } }) { Text("PAUSE") }
                        OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { db.setRunStatus(r.id, "RUNNING") }; enqueueRun(context, r.id); run = db.bulkRun(r.id) } }) { Text("RESUME") }
                        OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { db.setRunStatus(r.id, "CANCELLED") }; run = db.bulkRun(r.id) } }) { Text("CANCEL") }
                    }
                }
            }
        }
        Spacer(Modifier.height(26.dp))
    }
}

private fun parseTargets(input: String): List<String> = input.lineSequence()
    .flatMap { it.split(',', ';').asSequence() }
    .map { it.trim().trim('"', '\'') }
    .filter { it.isNotBlank() && !it.equals("domain", true) && !it.equals("url", true) && !it.equals("ip", true) }
    .distinct().toList()

private fun enqueueRun(context: Context, runId: String) {
    val request = OneTimeWorkRequestBuilder<BulkCheckWorker>()
        .setInputData(workDataOf(BulkCheckWorker.KEY_RUN_ID to runId)).build()
    WorkManager.getInstance(context).enqueue(request)
}

@Composable
private fun UixHistory(context: Context, db: HistoryDatabase, refresh: Int) {
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var rows by remember { mutableStateOf(emptyList<HistoryRow>()) }
    LaunchedEffect(refresh) { rows = withContext(Dispatchers.IO) { db.recent(5000) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(db.exportCsv(filter)) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("History", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Local response archive", color = UixMuted, fontSize = 11.sp)
            }
            OutlinedButton(onClick = { exportLauncher.launch("noway-${filter.lowercase()}-history.csv") }) { Text("EXPORT") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("ALL", "SUCCESS", "REDIRECTED", "RESPONDED", "FAILED").forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
            }
        }
        Spacer(Modifier.height(10.dp))
        val shown = if (filter == "ALL") rows else rows.filter { it.category == filter }
        if (shown.isEmpty()) EmptyPanel("No results in this category.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(shown, key = { it.id }) { HistoryLine(it) }
        }
    }
}

@Composable
private fun HistoryLine(row: HistoryRow) {
    val color = when (row.category) {
        "SUCCESS" -> UixCyan
        "REDIRECTED" -> UixPurple
        "RESPONDED" -> UixAmber
        else -> UixRed
    }
    Surface(color = UixSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(100.dp)).background(color))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(row.target, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(buildString {
                    append(row.category)
                    row.cdn?.let { append(" • $it") }
                    append(" • ${row.durationMs} ms")
                }, color = UixMuted, fontSize = 9.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(row.statusCode?.toString() ?: "—", color = color, fontWeight = FontWeight.Black)
                Text(formatTime(row.checkedAt), color = UixMuted, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = UixMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
}

@Composable
private fun EmptyPanel(message: String) {
    Surface(color = UixSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(18.dp), color = UixMuted, fontSize = 11.sp)
    }
}

private fun formatTime(ms: Long): String = SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault()).format(Date(ms))
