package com.noway.responsechecker.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.noway.responsechecker.network.ResponseChecker
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

private val Bg = Color(0xFF070A0F)
private val Panel = Color(0xFF0E141D)
private val Panel2 = Color(0xFF121C28)
private val Cyan = Color(0xFF37F3D2)
private val Violet = Color(0xFF8D7CFF)
private val Amber = Color(0xFFFFC857)
private val Red = Color(0xFFFF647C)
private val Muted = Color(0xFF8D9AAA)

private val nowayScheme = darkColorScheme(
    primary = Cyan,
    secondary = Violet,
    tertiary = Amber,
    background = Bg,
    surface = Panel,
    surfaceVariant = Panel2,
    onPrimary = Color(0xFF001A16),
    onBackground = Color(0xFFEAF7F5),
    onSurface = Color(0xFFEAF7F5),
    error = Red
)

@Composable
fun NowayApp() {
    MaterialTheme(colorScheme = nowayScheme) {
        var unlocked by rememberSaveable { mutableStateOf(false) }
        if (!unlocked) LoginScreen { unlocked = true } else MainShell()
    }
}

@Composable
private fun LoginScreen(onSuccess: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF132A31), Bg), center = Offset(250f, 220f), radius = 900f)
        ).padding(24.dp)
    ) {
        Column(Modifier.align(Alignment.Center).widthIn(max = 460.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SignalOrb()
            Spacer(Modifier.height(22.dp))
            Text("NOWAY", fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 7.sp)
            Text("RESPONSE CHECKER", color = Cyan, fontSize = 13.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text("Owner • Abdul Basit", color = Muted)
            Spacer(Modifier.height(30.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Access password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = error
            )
            if (error) Text("Incorrect password", color = Red, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (sha256(password) == "ddc791927b7836283b618b8c69107cdee841d28f47feffa5f91a5e5318228997") onSuccess() else error = true
                },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("ENTER CONSOLE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            Spacer(Modifier.height(16.dp))
            Text("Local-first diagnostics • No cloud account required", color = Muted, fontSize = 12.sp)
        }
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

@Composable
private fun SignalOrb() {
    Canvas(Modifier.size(112.dp)) {
        val c = center
        drawCircle(Cyan.copy(alpha = .08f), radius = size.minDimension * .50f)
        drawCircle(Cyan.copy(alpha = .18f), radius = size.minDimension * .36f, style = Stroke(2.dp.toPx()))
        drawArc(Cyan, -42f, 235f, false, topLeft = Offset(14f,14f), size = Size(size.width-28f,size.height-28f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        drawArc(Violet, 210f, 82f, false, topLeft = Offset(26f,26f), size = Size(size.width-52f,size.height-52f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Cyan, radius = 7.dp.toPx(), center = c)
    }
}

private enum class Tab(val title: String, val symbol: String) {
    DASH("Pulse", "◉"), CHECK("Check", "↗"), BULK("Bulk", "≋"), HISTORY("History", "⌁")
}

@Composable
private fun MainShell() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { HistoryDatabase(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { db.close() } }
    var tab by rememberSaveable { mutableStateOf(Tab.DASH) }
    var refresh by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Surface(color = Bg) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NOWAY", fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontSize = 20.sp)
                        Text("Response intelligence console", color = Muted, fontSize = 11.sp)
                    }
                    Box(Modifier.clip(RoundedCornerShape(100)).background(Cyan.copy(alpha=.12f)).border(1.dp, Cyan.copy(alpha=.35f), RoundedCornerShape(100)).padding(horizontal=10.dp, vertical=6.dp)) {
                        Text("● LIVE", color=Cyan, fontSize=11.sp, fontWeight=FontWeight.Bold)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF090E15)) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.symbol, fontSize = 19.sp) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when(tab) {
                Tab.DASH -> DashboardScreen(db, refresh)
                Tab.CHECK -> CheckerScreen(db) { refresh++ }
                Tab.BULK -> BulkScreen(context, db) { refresh++ }
                Tab.HISTORY -> HistoryScreen(context, db, refresh)
            }
        }
    }
}

@Composable
private fun DashboardScreen(db: HistoryDatabase, refresh: Int) {
    var counts by remember { mutableStateOf(emptyMap<ResultClass, Int>()) }
    var recent by remember { mutableStateOf(emptyList<HistoryRow>()) }
    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) { counts = db.counts(); recent = db.recent(8) }
    }
    val total = counts.values.sum()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp), contentPadding = PaddingValues(bottom=24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HeroPanel(total, counts)
        }
        item {
            Text("RESPONSE MATRIX", color=Muted, fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=2.sp, modifier=Modifier.padding(top=4.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("SUCCESS", counts[ResultClass.SUCCESS] ?: 0, Cyan, Modifier.weight(1f))
                StatCard("REDIRECT", counts[ResultClass.REDIRECTED] ?: 0, Violet, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("RESPONDED", counts[ResultClass.RESPONDED] ?: 0, Amber, Modifier.weight(1f))
                StatCard("FAILED", counts[ResultClass.FAILED] ?: 0, Red, Modifier.weight(1f))
            }
        }
        item { Text("RECENT SIGNALS", color=Muted, fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=2.sp, modifier=Modifier.padding(top=6.dp)) }
        if (recent.isEmpty()) item { EmptyPanel("No checks yet. Open Check or Bulk to start.") }
        else items(recent) { HistoryLine(it) }
    }
}

@Composable
private fun HeroPanel(total: Int, counts: Map<ResultClass, Int>) {
    Surface(shape=RoundedCornerShape(24.dp), color=Panel2, modifier=Modifier.fillMaxWidth().padding(top=4.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment=Alignment.CenterVertically) {
            Canvas(Modifier.size(86.dp)) {
                val stroke=8.dp.toPx(); val good=(counts[ResultClass.SUCCESS] ?: 0).toFloat(); val all=total.coerceAtLeast(1).toFloat(); val sweep=good/all*300f
                drawArc(Color(0xFF22303D), -240f, 300f, false, style=Stroke(stroke, cap=StrokeCap.Round))
                drawArc(Cyan, -240f, sweep, false, style=Stroke(stroke, cap=StrokeCap.Round))
            }
            Column(Modifier.padding(start=18.dp)) {
                Text("NETWORK PULSE", color=Cyan, fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=2.sp)
                Text("$total", fontSize=36.sp, fontWeight=FontWeight.Black)
                Text("stored response observations", color=Muted, fontSize=12.sp)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier=modifier, shape=RoundedCornerShape(18.dp), color=Panel) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color=color, fontSize=10.sp, letterSpacing=1.5.sp, fontWeight=FontWeight.Bold)
            Text(count.toString(), fontSize=28.sp, fontWeight=FontWeight.Black)
        }
    }
}

@Composable
private fun CheckerScreen(db: HistoryDatabase, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var target by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("GET") }
    var headersText by rememberSaveable { mutableStateOf("User-Agent: NOWAY-Response-Checker/1.0") }
    var body by rememberSaveable { mutableStateOf("") }
    var follow by rememberSaveable { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CheckResult?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle("SINGLE RESPONSE PROBE", "Inspect HTTP, redirects, DNS, TLS and CDN evidence")
        OutlinedTextField(target, {target=it}, label={Text("Domain / IP / URL")}, placeholder={Text("example.com")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("GET","HEAD","POST").forEach { m -> FilterChip(selected=method==m, onClick={method=m}, label={Text(m)}) }
            Spacer(Modifier.weight(1f))
            Text("Follow redirects", color=Muted, fontSize=12.sp, modifier=Modifier.align(Alignment.CenterVertically))
            Switch(checked=follow, onCheckedChange={follow=it})
        }
        OutlinedTextField(headersText, {headersText=it}, label={Text("Custom headers • one per line")}, modifier=Modifier.fillMaxWidth().heightIn(min=100.dp), textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace, fontSize=12.sp))
        AnimatedVisibility(method=="POST") { OutlinedTextField(body,{body=it}, label={Text("Request body")}, modifier=Modifier.fillMaxWidth().heightIn(min=90.dp)) }
        Button(
            enabled=!loading && target.isNotBlank(),
            onClick={
                loading=true
                scope.launch {
                    val parsed = parseHeaders(headersText)
                    val r = withContext(Dispatchers.IO) { ResponseChecker().check(target, method, parsed, follow, 12, body) }
                    withContext(Dispatchers.IO) { db.save(r) }
                    result=r; loading=false; onSaved()
                }
            },
            modifier=Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if(loading) "PROBING…" else "CHECK RESPONSE", fontWeight=FontWeight.Bold) }
        result?.let { ResultPanel(it) }
    }
}

private fun parseHeaders(text: String): Map<String,String> = text.lineSequence().mapNotNull { line ->
    val i=line.indexOf(':'); if(i<=0) null else line.substring(0,i).trim() to line.substring(i+1).trim()
}.filter { it.first.isNotBlank() }.toMap()

@Composable
private fun ResultPanel(r: CheckResult) {
    val color = when(r.resultClass){ ResultClass.SUCCESS->Cyan; ResultClass.REDIRECTED->Violet; ResultClass.RESPONDED->Amber; ResultClass.FAILED->Red }
    Surface(shape=RoundedCornerShape(20.dp), color=Panel, modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(100)).background(color))
                Text("  ${r.resultClass.name}", color=color, fontWeight=FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(r.statusCode?.toString() ?: "—", fontSize=24.sp, fontWeight=FontWeight.Black)
            }
            KeyValue("Final URL", r.finalUrl)
            KeyValue("Total / DNS", "${r.durationMs} ms / ${r.dnsMs ?: "—"} ms")
            if(r.ipAddresses.isNotEmpty()) KeyValue("Resolved IPs", r.ipAddresses.joinToString(" • "))
            r.cdn?.let {
                Surface(color=Cyan.copy(alpha=.08f), shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("CDN LENS • ${it.provider}", color=Cyan, fontWeight=FontWeight.Bold, fontSize=12.sp)
                        Text("Confidence: ${it.confidence} • ${it.evidence.joinToString(" • ")}", color=Muted, fontSize=12.sp)
                    }
                }
            }
            r.tls?.let { tls ->
                Text("TLS", color=Muted, fontWeight=FontWeight.Bold, fontSize=11.sp, letterSpacing=1.5.sp)
                KeyValue("Protocol", tls.protocol ?: "—"); KeyValue("Cipher", tls.cipherSuite ?: "—")
                tls.subject?.let { KeyValue("Certificate", it) }
                tls.issuer?.let { KeyValue("Issuer", it) }
            }
            if(r.redirectChain.isNotEmpty()) {
                Text("REDIRECT CHAIN", color=Violet, fontWeight=FontWeight.Bold, fontSize=11.sp, letterSpacing=1.5.sp)
                r.redirectChain.forEachIndexed { i, hop ->
                    Text("${i+1}. ${hop.code}  ${hop.from}\n    ↳ ${hop.to}", fontFamily=FontFamily.Monospace, fontSize=11.sp, color=Color(0xFFC7D1DC))
                }
            }
            if(r.headers.isNotEmpty()) {
                Text("RESPONSE HEADERS", color=Muted, fontWeight=FontWeight.Bold, fontSize=11.sp, letterSpacing=1.5.sp)
                SelectionContainer { Text(r.headers.entries.sortedBy{it.key.lowercase()}.joinToString("\n") { "${it.key}: ${it.value.joinToString(", ")}" }, fontFamily=FontFamily.Monospace, fontSize=11.sp) }
            }
            if(r.bodyPreview.isNotBlank()) {
                Text("BODY PREVIEW • first 64 KiB", color=Muted, fontWeight=FontWeight.Bold, fontSize=11.sp, letterSpacing=1.5.sp)
                Surface(color=Bg, shape=RoundedCornerShape(12.dp), modifier=Modifier.fillMaxWidth()) {
                    SelectionContainer { Text(r.bodyPreview, Modifier.padding(12.dp), fontFamily=FontFamily.Monospace, fontSize=10.sp, maxLines=30, overflow=TextOverflow.Ellipsis) }
                }
            }
            r.error?.let { Text(it, color=Red, fontFamily=FontFamily.Monospace, fontSize=12.sp) }
        }
    }
}

@Composable
private fun BulkScreen(context: Context, db: HistoryDatabase, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("noway", Context.MODE_PRIVATE) }
    var input by rememberSaveable { mutableStateOf("") }
    var runId by rememberSaveable { mutableStateOf(prefs.getString("last_run", null)) }
    var run by remember { mutableStateOf<BulkRun?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch { input = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() } }
    }

    LaunchedEffect(runId) {
        while(true) {
            val id=runId
            if(id!=null) run=withContext(Dispatchers.IO){db.bulkRun(id)}
            delay(750)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle("BULK RESPONSE ENGINE", "Queue 1K+ targets, persist progress, classify every result")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={importLauncher.launch(arrayOf("text/plain","text/csv","application/json","*/*"))}) { Text("IMPORT FILE") }
            Text("TXT / CSV lines supported", color=Muted, fontSize=11.sp, modifier=Modifier.align(Alignment.CenterVertically))
        }
        OutlinedTextField(input,{input=it}, label={Text("One domain / IP / URL per line")}, modifier=Modifier.fillMaxWidth().heightIn(min=180.dp), textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace, fontSize=11.sp))
        val targetCount = remember(input) { parseTargets(input).size }
        Text("$targetCount unique targets ready", color=if(targetCount>0) Cyan else Muted, fontWeight=FontWeight.Bold)
        Button(
            enabled=targetCount>0,
            onClick={
                scope.launch {
                    val id=withContext(Dispatchers.IO){db.createBulkRun(parseTargets(input))}
                    runId=id; prefs.edit().putString("last_run",id).apply(); enqueueRun(context,id); onChanged()
                }
            }, modifier=Modifier.fillMaxWidth().height(52.dp)
        ){ Text("START PERSISTENT BULK CHECK", fontWeight=FontWeight.Bold) }

        run?.let { r ->
            Surface(color=Panel, shape=RoundedCornerShape(20.dp), modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("ACTIVE RUN", color=Cyan, fontWeight=FontWeight.Bold, fontSize=11.sp, letterSpacing=2.sp)
                    Text("${r.completed} / ${r.total}", fontSize=30.sp, fontWeight=FontWeight.Black)
                    LinearProgressIndicator(progress={ if(r.total==0) 0f else r.completed.toFloat()/r.total }, modifier=Modifier.fillMaxWidth())
                    Text("${r.status} • ${r.pending} pending", color=Muted)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={ scope.launch { withContext(Dispatchers.IO){db.setRunStatus(r.id,"PAUSED")}; run=db.bulkRun(r.id) } }) { Text("PAUSE") }
                        OutlinedButton(onClick={ scope.launch { withContext(Dispatchers.IO){db.setRunStatus(r.id,"RUNNING")}; enqueueRun(context,r.id); run=db.bulkRun(r.id) } }) { Text("RESUME") }
                        OutlinedButton(onClick={ scope.launch { withContext(Dispatchers.IO){db.setRunStatus(r.id,"CANCELLED")}; run=db.bulkRun(r.id) } }) { Text("CANCEL") }
                    }
                }
            }
        }
        Text("Bulk mode uses Android WorkManager, so queued work is designed to survive app restarts and resume under Android's background-work rules.", color=Muted, fontSize=11.sp)
    }
}

private fun parseTargets(input: String): List<String> = input.lineSequence()
    .flatMap { it.split(',', ';').asSequence() }
    .map { it.trim().trim('"','\'') }
    .filter { it.isNotBlank() && !it.equals("domain",true) && !it.equals("url",true) && !it.equals("ip",true) }
    .distinct().toList()

private fun enqueueRun(context: Context, runId: String) {
    val request = OneTimeWorkRequestBuilder<BulkCheckWorker>()
        .setInputData(workDataOf(BulkCheckWorker.KEY_RUN_ID to runId)).build()
    WorkManager.getInstance(context).enqueue(request)
}

@Composable
private fun HistoryScreen(context: Context, db: HistoryDatabase, refresh: Int) {
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var rows by remember { mutableStateOf(emptyList<HistoryRow>()) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh,reload) { rows=withContext(Dispatchers.IO){db.recent(5000)} }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if(uri!=null) context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(db.exportCsv()) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=10.dp), verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){ Text("HISTORY VAULT", fontWeight=FontWeight.Black, fontSize=22.sp); Text("Local SQLite result archive", color=Muted, fontSize=11.sp) }
            OutlinedButton(onClick={exportLauncher.launch("noway-response-history.csv")}){ Text("EXPORT CSV") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("ALL","SUCCESS","REDIRECTED","RESPONDED","FAILED").forEach { f -> FilterChip(selected=filter==f,onClick={filter=f},label={Text(f)}) }
        }
        Spacer(Modifier.height(8.dp))
        val shown=if(filter=="ALL") rows else rows.filter{it.category==filter}
        if(shown.isEmpty()) EmptyPanel("No results in this category.") else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp), contentPadding=PaddingValues(bottom=20.dp)) {
            items(shown,key={it.id}){HistoryLine(it)}
        }
    }
}

@Composable
private fun HistoryLine(row: HistoryRow) {
    val color=when(row.category){"SUCCESS"->Cyan;"REDIRECTED"->Violet;"RESPONDED"->Amber;else->Red}
    Surface(color=Panel, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(100)).background(color))
            Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                Text(row.target, fontWeight=FontWeight.SemiBold, maxLines=1, overflow=TextOverflow.Ellipsis)
                Text(buildString { append(row.category); row.cdn?.let{append(" • $it")}; append(" • ${row.durationMs} ms") }, color=Muted, fontSize=10.sp)
            }
            Column(horizontalAlignment=Alignment.End) {
                Text(row.statusCode?.toString() ?: "—", color=color, fontWeight=FontWeight.Black)
                Text(formatTime(row.checkedAt), color=Muted, fontSize=9.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(title:String, subtitle:String){ Column { Text(title,fontSize=22.sp,fontWeight=FontWeight.Black); Text(subtitle,color=Muted,fontSize=11.sp) } }

@Composable
private fun KeyValue(key:String,value:String){ Column { Text(key.uppercase(), color=Muted,fontSize=9.sp,letterSpacing=1.sp); SelectionContainer{Text(value,fontSize=12.sp,fontFamily=FontFamily.Monospace)} } }

@Composable
private fun EmptyPanel(message:String){ Surface(color=Panel,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Text(message,Modifier.padding(18.dp),color=Muted)} }

private fun formatTime(ms:Long):String=SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault()).format(Date(ms))
