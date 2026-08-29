package com.noway.responsechecker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noway.responsechecker.cdn.CdnResult
import com.noway.responsechecker.cdn.CdnScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg0 = Color(0xFF070A0F)
private val Bg1 = Color(0xFF0C111A)
private val Panel = Color(0xFF111824)
private val Panel2 = Color(0xFF151F2D)
private val Accent = Color(0xFF00E5A8)
private val AccentBlue = Color(0xFF58A6FF)
private val Danger = Color(0xFFFF5D73)
private val Warning = Color(0xFFFFC857)
private val Muted = Color(0xFF8B9AAF)
private val Line = Color(0xFF243244)

private enum class ResultFilter(val label: String) {
    ALL("All"), DETECTED("Detected"), UNKNOWN("Unknown"), ERROR("Errors")
}

private enum class ExportMode(val label: String) {
    ALL("Full results"), DETECTED("Detected only"), HOSTS("Detected hosts only"), GROUPED("Group by CDN")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CdnFinderApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { CdnScanner() }
    val snackbar = remember { SnackbarHostState() }
    val results = remember { mutableStateListOf<CdnResult>() }

    var input by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ResultFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var completed by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var concurrency by remember { mutableIntStateOf(6) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var exportOpen by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf("") }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null && pendingExport.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) }
            }.onSuccess {
                scope.launch { snackbar.showSnackbar("TXT exported successfully") }
            }.onFailure {
                scope.launch { snackbar.showSnackbar("Export failed: ${it.message ?: "unknown error"}") }
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.onSuccess { text ->
                input = text.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(3000)
                    .joinToString("\n")
                scope.launch { snackbar.showSnackbar("TXT imported") }
            }.onFailure {
                scope.launch { snackbar.showSnackbar("Import failed") }
            }
        }
    }

    fun parseTargets(): List<String> = input
        .split('\n', ',', ';', ' ', '\t')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { scanner.normalizeHost(it) ?: it.lowercase(Locale.US) }
        .take(1500)

    fun startScan() {
        val targets = parseTargets()
        if (targets.isEmpty()) {
            scope.launch { snackbar.showSnackbar("Add at least one domain or URL") }
            return
        }
        scanJob?.cancel()
        results.clear()
        completed = 0
        total = targets.size
        isScanning = true
        scanJob = scope.launch {
            val gate = Semaphore(concurrency)
            try {
                targets.map { raw ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            val result = scanner.scan(raw)
                            withContext(Dispatchers.Main) {
                                results.add(result)
                                completed += 1
                            }
                        }
                    }
                }.awaitAll()
            } catch (_: CancellationException) {
            } finally {
                isScanning = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        isScanning = false
    }

    fun buildExport(mode: ExportMode): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val detected = results.filter { it.detected }
        val body = when (mode) {
            ExportMode.ALL -> results.joinToString("\n") { it.compactLine() }
            ExportMode.DETECTED -> detected.joinToString("\n") { it.compactLine() }
            ExportMode.HOSTS -> detected.map { it.host }.distinct().joinToString("\n")
            ExportMode.GROUPED -> detected.groupBy { it.provider }
                .toSortedMap()
                .entries.joinToString("\n\n") { (provider, list) ->
                    buildString {
                        append("[$provider]\n")
                        append(list.map { it.host }.distinct().sorted().joinToString("\n"))
                    }
                }
        }
        return buildString {
            append("NOWAY CDN FINDER R5\n")
            append("Exported: $stamp\n")
            append("Mode: ${mode.label}\n")
            append("Results: ${results.size} | Detected: ${detected.size}\n")
            append("----------------------------------------\n")
            append(body)
            append('\n')
        }
    }

    val filteredResults = results.filter { result ->
        val filterOk = when (filter) {
            ResultFilter.ALL -> true
            ResultFilter.DETECTED -> result.detected
            ResultFilter.UNKNOWN -> !result.detected && result.error == null
            ResultFilter.ERROR -> result.error != null
        }
        val queryOk = query.isBlank() || result.host.contains(query, true) || result.provider.contains(query, true)
        filterOk && queryOk
    }

    NowayTheme {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg0.copy(alpha = 0.96f)),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Brush.linearGradient(listOf(Accent, AccentBlue))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("N5", color = Bg0, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text("NOWAY CDN FINDER", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text("R5 • EDGE INTELLIGENCE", color = Accent, fontSize = 10.sp, letterSpacing = 1.2.sp)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { openDocument.launch(arrayOf("text/plain", "text/*")) }) {
                            Icon(Icons.Rounded.FileOpen, "Import TXT", tint = AccentBlue)
                        }
                        Box {
                            IconButton(enabled = results.isNotEmpty(), onClick = { exportOpen = true }) {
                                Icon(Icons.Rounded.Download, "Export TXT", tint = if (results.isNotEmpty()) Accent else Muted)
                            }
                            DropdownMenu(expanded = exportOpen, onDismissRequest = { exportOpen = false }) {
                                ExportMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        onClick = {
                                            exportOpen = false
                                            pendingExport = buildExport(mode)
                                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                                            createDocument.launch("noway-cdn-$stamp.txt")
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Bg0, Bg1, Color(0xFF091018))))
                    .padding(padding)
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Dashboard(results = results, completed = completed, total = total)
                    Spacer(Modifier.height(10.dp))

                    InputPanel(
                        input = input,
                        onInputChange = { input = it },
                        scanning = isScanning,
                        concurrency = concurrency,
                        onConcurrency = { concurrency = it },
                        onScan = ::startScan,
                        onStop = ::stopScan,
                        onPaste = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (text.isNotBlank()) input = text
                        },
                        onClear = {
                            stopScan()
                            input = ""
                            results.clear()
                            completed = 0
                            total = 0
                        }
                    )

                    AnimatedVisibility(visible = isScanning) {
                        Column(Modifier.padding(top = 10.dp)) {
                            LinearProgressIndicator(
                                progress = { if (total == 0) 0f else completed.toFloat() / total.toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                                color = Accent,
                                trackColor = Line
                            )
                            Spacer(Modifier.height(5.dp))
                            Text("Scanning $completed / $total • $concurrency parallel workers", color = Muted, fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    ResultsToolbar(
                        filter = filter,
                        onFilter = { filter = it },
                        query = query,
                        onQuery = { query = it },
                        count = filteredResults.size,
                        onCopy = {
                            val text = filteredResults.joinToString("\n") { it.compactLine() }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("NOWAY CDN results", text))
                            scope.launch { snackbar.showSnackbar("Visible results copied") }
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                    if (results.isEmpty()) {
                        EmptyState(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredResults, key = { "${it.host}-${it.durationMs}-${it.input}" }) { item ->
                                ResultCard(item)
                            }
                            item { Spacer(Modifier.height(10.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(results: List<CdnResult>, completed: Int, total: Int) {
    val detected = results.count { it.detected }
    val unknown = results.count { !it.detected && it.error == null }
    val errors = results.count { it.error != null }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard("DONE", "$completed/$total", AccentBlue, Modifier.weight(1f))
        StatCard("CDN", detected.toString(), Accent, Modifier.weight(1f))
        StatCard("UNKNOWN", unknown.toString(), Warning, Modifier.weight(1f))
        StatCard("ERROR", errors.toString(), Danger, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = Panel, tonalElevation = 0.dp) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 0.7.sp)
        }
    }
}

@Composable
private fun InputPanel(
    input: String,
    onInputChange: (String) -> Unit,
    scanning: Boolean,
    concurrency: Int,
    onConcurrency: (Int) -> Unit,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Public, null, tint = Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("TARGETS", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp)
                Spacer(Modifier.weight(1f))
                Text("up to 1,500", color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth().height(118.dp),
                placeholder = { Text("example.com\ncdn.example.org\nhttps://site.tld/path", color = Muted) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                shape = RoundedCornerShape(13.dp)
            )
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Workers", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                listOf(3, 6, 10).forEach { option ->
                    FilterChip(
                        selected = concurrency == option,
                        onClick = { if (!scanning) onConcurrency(option) },
                        enabled = !scanning,
                        label = { Text(option.toString(), fontSize = 10.sp) },
                        modifier = Modifier.padding(end = 5.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onPaste, enabled = !scanning) { Text("Paste", fontSize = 11.sp) }
                TextButton(onClick = onClear) { Text("Clear", color = Danger, fontSize = 11.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!scanning) {
                    Button(
                        onClick = onScan,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg0)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("SCAN CDN", fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Bg0)
                    ) {
                        Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("STOP", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsToolbar(
    filter: ResultFilter,
    onFilter: (ResultFilter) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    onCopy: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("RESULTS", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(Modifier.width(7.dp))
            Text("$count", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCopy, enabled = count > 0, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.ContentCopy, "Copy", tint = if (count > 0) AccentBlue else Muted, modifier = Modifier.size(18.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ResultFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { onFilter(item) },
                    label = { Text(item.label, fontSize = 10.sp) }
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Filter by domain or CDN provider", color = Muted, fontSize = 11.sp) },
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
        )
    }
}

@Composable
private fun ResultCard(result: CdnResult) {
    var expanded by remember(result.host, result.durationMs) { mutableStateOf(false) }
    val statusColor = when {
        result.error != null -> Danger
        result.detected -> Accent
        else -> Warning
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Panel2),
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 4.dp).size(9.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        result.host,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (result.error != null) result.error else result.provider,
                        color = if (result.detected) Accent else if (result.error != null) Danger else Warning,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (result.detected) {
                    Surface(shape = RoundedCornerShape(9.dp), color = Accent.copy(alpha = 0.13f)) {
                        Text("${result.confidence}%", color = Accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MiniTag("HTTP", result.httpCode?.toString() ?: "—")
                MiniTag("DNS", if (result.ips.isNotEmpty()) "OK" else "—")
                MiniTag("TIME", "${result.durationMs}ms")
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    DetailLine("Scheme", result.scheme ?: "—")
                    DetailLine("CNAME", result.cname ?: "—")
                    DetailLine("IPs", if (result.ips.isEmpty()) "—" else result.ips.joinToString(", "))
                    DetailLine("Evidence", if (result.evidence.isEmpty()) "No strong CDN fingerprint" else result.evidence.joinToString(" • "))
                }
            }
        }
    }
}

@Composable
private fun MiniTag(label: String, value: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = Bg0.copy(alpha = 0.65f)) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$label ", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White.copy(alpha = 0.88f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = Muted, fontSize = 10.sp, modifier = Modifier.width(68.dp))
        Text(value, color = Color.White.copy(alpha = 0.86f), fontSize = 10.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Accent.copy(alpha = 0.08f)).border(1.dp, Accent.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Shield, null, tint = Accent, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("READY TO IDENTIFY EDGE NETWORKS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text("Paste domains, scan, inspect evidence, then export to TXT.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NowayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Accent,
            secondary = AccentBlue,
            background = Bg0,
            surface = Panel,
            onPrimary = Bg0,
            onBackground = Color(0xFFF3F7FA),
            onSurface = Color(0xFFF3F7FA),
            error = Danger,
            outline = Line
        ),
        content = content
    )
}
