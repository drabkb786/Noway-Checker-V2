package com.noway.responsechecker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noway.responsechecker.data.HistoryDatabase
import com.noway.responsechecker.network.ResponseChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class BulkCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val runId = inputData.getString(KEY_RUN_ID) ?: return@withContext Result.failure()
        val db = HistoryDatabase(applicationContext)
        val leaseOwner = id.toString()
        if (!db.acquireWorkerLease(runId, leaseOwner)) {
            db.close()
            return@withContext Result.success()
        }
        try {
            db.resetInProgress(runId)
            while (true) {
                if (isStopped) {
                    db.resetInProgress(runId)
                    return@withContext Result.retry()
                }
                val run = db.bulkRun(runId) ?: return@withContext Result.failure()
                when (run.status) {
                    "PAUSED", "CANCELLED" -> {
                        db.resetInProgress(runId)
                        return@withContext Result.success()
                    }
                }
                val batch = db.claimPendingBatch(runId, run.concurrency)
                if (batch.isEmpty()) break
                coroutineScope {
                    batch.map { (id, target) ->
                        async(Dispatchers.IO) {
                            val result = ResponseChecker(applicationContext).check(
                                rawTarget = target,
                                followRedirects = true,
                                captureBody = false,
                                cdnFinder = true
                            )
                            db.save(result)
                            db.markTarget(id)
                        }
                    }.awaitAll()
                }
                db.renewWorkerLease(runId, leaseOwner)
                setProgress(androidx.work.workDataOf("completed" to (db.bulkRun(runId)?.completed ?: 0)))
            }
            db.setRunStatus(runId, "COMPLETED")
            Result.success()
        } catch (t: Throwable) {
            db.resetInProgress(runId)
            Result.retry()
        } finally {
            db.releaseWorkerLease(runId, leaseOwner)
            db.close()
        }
    }

    companion object { const val KEY_RUN_ID = "run_id" }
}
