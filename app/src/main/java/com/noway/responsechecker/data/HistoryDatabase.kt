package com.noway.responsechecker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.noway.responsechecker.network.CheckResult
import com.noway.responsechecker.network.ResultClass
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class HistoryRow(
    val id: Long,
    val target: String,
    val finalUrl: String,
    val statusCode: Int?,
    val category: String,
    val durationMs: Long,
    val cdn: String?,
    val error: String?,
    val checkedAt: Long
)

data class BulkRun(
    val id: String,
    val total: Int,
    val pending: Int,
    val completed: Int,
    val status: String,
    val concurrency: Int
)

class HistoryDatabase(context: Context) : SQLiteOpenHelper(context, "noway.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE history(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                target TEXT NOT NULL,
                normalized_url TEXT NOT NULL,
                final_url TEXT NOT NULL,
                method TEXT NOT NULL,
                status_code INTEGER,
                status_message TEXT,
                category TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                dns_ms INTEGER,
                ips TEXT,
                headers_json TEXT,
                body_preview TEXT,
                redirects_json TEXT,
                tls_json TEXT,
                cdn TEXT,
                error TEXT,
                checked_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE bulk_runs(
                id TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                concurrency INTEGER NOT NULL DEFAULT 6,
                lease_owner TEXT,
                lease_until INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE bulk_targets(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT NOT NULL,
                target TEXT NOT NULL,
                state TEXT NOT NULL DEFAULT 'PENDING',
                UNIQUE(run_id, target)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_bulk_pending ON bulk_targets(run_id, state)")
        db.execSQL("CREATE INDEX idx_history_checked ON history(checked_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE bulk_runs ADD COLUMN concurrency INTEGER NOT NULL DEFAULT 6")
            db.execSQL("ALTER TABLE bulk_runs ADD COLUMN lease_owner TEXT")
            db.execSQL("ALTER TABLE bulk_runs ADD COLUMN lease_until INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun save(result: CheckResult) {
        val headersJson = JSONObject().apply { result.headers.forEach { (k, v) -> put(k, JSONArray(v)) } }.toString()
        val redirectsJson = JSONArray().apply { result.redirectChain.forEach { put(JSONObject().put("code", it.code).put("from", it.from).put("to", it.to)) } }.toString()
        val tlsJson = result.tls?.let { JSONObject().put("protocol", it.protocol).put("cipher", it.cipherSuite).put("subject", it.subject).put("issuer", it.issuer).put("validUntil", it.validUntil).toString() }
        writableDatabase.insert("history", null, ContentValues().apply {
            put("target", result.target); put("normalized_url", result.normalizedUrl); put("final_url", result.finalUrl)
            put("method", result.method); result.statusCode?.let { put("status_code", it) }; put("status_message", result.statusMessage)
            put("category", result.resultClass.name); put("duration_ms", result.durationMs); result.dnsMs?.let { put("dns_ms", it) }
            put("ips", result.ipAddresses.joinToString(",")); put("headers_json", headersJson); put("body_preview", result.bodyPreview)
            put("redirects_json", redirectsJson); put("tls_json", tlsJson); put("cdn", result.cdn?.provider); put("error", result.error); put("checked_at", result.checkedAt)
        })
    }

    fun recent(limit: Int = 200): List<HistoryRow> = readableDatabase.rawQuery(
        "SELECT id,target,final_url,status_code,category,duration_ms,cdn,error,checked_at FROM history ORDER BY checked_at DESC LIMIT ?",
        arrayOf(limit.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(HistoryRow(
                id = c.getLong(0), target = c.getString(1), finalUrl = c.getString(2),
                statusCode = if (c.isNull(3)) null else c.getInt(3), category = c.getString(4),
                durationMs = c.getLong(5), cdn = if (c.isNull(6)) null else c.getString(6),
                error = if (c.isNull(7)) null else c.getString(7), checkedAt = c.getLong(8)
            ))
        }
    }

    fun counts(): Map<ResultClass, Int> {
        val result = ResultClass.entries.associateWith { 0 }.toMutableMap()
        readableDatabase.rawQuery("SELECT category,COUNT(*) FROM history GROUP BY category", null).use { c ->
            while (c.moveToNext()) runCatching { ResultClass.valueOf(c.getString(0)) }.getOrNull()?.let { result[it] = c.getInt(1) }
        }
        return result
    }

    fun acquireWorkerLease(runId: String, owner: String, leaseMs: Long = 300_000L): Boolean {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        return try {
            val current = db.rawQuery("SELECT lease_owner,lease_until FROM bulk_runs WHERE id=?", arrayOf(runId)).use { c ->
                if (c.moveToFirst()) (if (c.isNull(0)) null else c.getString(0)) to c.getLong(1) else return@use null
            } ?: return false
            val allowed = current.first == null || current.first == owner || current.second < now
            if (allowed) {
                db.update("bulk_runs", ContentValues().apply { put("lease_owner", owner); put("lease_until", now + leaseMs) }, "id=?", arrayOf(runId))
                db.setTransactionSuccessful()
            }
            allowed
        } finally { db.endTransaction() }
    }

    fun renewWorkerLease(runId: String, owner: String, leaseMs: Long = 300_000L) {
        writableDatabase.update("bulk_runs", ContentValues().apply { put("lease_until", System.currentTimeMillis() + leaseMs) }, "id=? AND lease_owner=?", arrayOf(runId, owner))
    }

    fun releaseWorkerLease(runId: String, owner: String) {
        writableDatabase.update("bulk_runs", ContentValues().apply { putNull("lease_owner"); put("lease_until", 0L) }, "id=? AND lease_owner=?", arrayOf(runId, owner))
    }

    fun createBulkRun(targets: List<String>, concurrency: Int = 6): String {
        val runId = UUID.randomUUID().toString()
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertOrThrow("bulk_runs", null, ContentValues().apply { put("id", runId); put("status", "RUNNING"); put("concurrency", concurrency.coerceIn(1, 20)); put("created_at", System.currentTimeMillis()) })
            targets.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { target ->
                writableDatabase.insertWithOnConflict("bulk_targets", null, ContentValues().apply { put("run_id", runId); put("target", target); put("state", "PENDING") }, SQLiteDatabase.CONFLICT_IGNORE)
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        return runId
    }

    fun claimPendingBatch(runId: String, limit: Int): List<Pair<Long, String>> {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val rows = db.rawQuery(
                "SELECT id,target FROM bulk_targets WHERE run_id=? AND state='PENDING' ORDER BY id LIMIT ?",
                arrayOf(runId, limit.coerceIn(1, 20).toString())
            ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) } }
            rows.forEach { (id, _) -> db.update("bulk_targets", ContentValues().apply { put("state", "IN_PROGRESS") }, "id=?", arrayOf(id.toString())) }
            db.setTransactionSuccessful()
            rows
        } finally { db.endTransaction() }
    }

    fun resetInProgress(runId: String) { writableDatabase.update("bulk_targets", ContentValues().apply { put("state", "PENDING") }, "run_id=? AND state='IN_PROGRESS'", arrayOf(runId)) }
    fun markTarget(id: Long, state: String = "DONE") { writableDatabase.update("bulk_targets", ContentValues().apply { put("state", state) }, "id=?", arrayOf(id.toString())) }
    fun setRunStatus(runId: String, status: String) { writableDatabase.update("bulk_runs", ContentValues().apply { put("status", status) }, "id=?", arrayOf(runId)) }
    fun runStatus(runId: String): String = readableDatabase.rawQuery("SELECT status FROM bulk_runs WHERE id=?", arrayOf(runId)).use { c -> if (c.moveToFirst()) c.getString(0) else "UNKNOWN" }

    fun bulkRun(runId: String): BulkRun? {
        val runMeta = readableDatabase.rawQuery("SELECT status,concurrency FROM bulk_runs WHERE id=?", arrayOf(runId)).use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getInt(1) else null
        } ?: return null
        val counts = readableDatabase.rawQuery("SELECT state,COUNT(*) FROM bulk_targets WHERE run_id=? GROUP BY state", arrayOf(runId)).use { c ->
            val map = mutableMapOf<String, Int>(); while (c.moveToNext()) map[c.getString(0)] = c.getInt(1); map
        }
        val total = counts.values.sum()
        val pending = (counts["PENDING"] ?: 0) + (counts["IN_PROGRESS"] ?: 0)
        val completed = counts["DONE"] ?: 0
        return BulkRun(runId, total, pending, completed, runMeta.first, runMeta.second)
    }

    fun exportCsv(category: String? = null): String {
        val rows = recent(100000).let { all -> if (category.isNullOrBlank() || category == "ALL") all else all.filter { it.category == category } }
        fun q(v: Any?) = "\"${(v?.toString() ?: "").replace("\"", "\"\"")}\""
        return buildString {
            appendLine("target,final_url,status,category,duration_ms,cdn,error,checked_at")
            rows.forEach { appendLine(listOf(it.target,it.finalUrl,it.statusCode,it.category,it.durationMs,it.cdn,it.error,it.checkedAt).joinToString(",") { v -> q(v) }) }
        }
    }
}
