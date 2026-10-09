package app.ocrpdf.arabic.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.ocrpdf.arabic.OcrApp
import app.ocrpdf.arabic.R
import app.ocrpdf.arabic.data.AppDb
import app.ocrpdf.arabic.data.JobStage
import app.ocrpdf.arabic.data.JobStatus
import app.ocrpdf.arabic.pdf.PdfInvalidException
import app.ocrpdf.arabic.pdf.PdfPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OcrWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val dao = AppDb.get(applicationContext).jobDao()
        val job = dao.get(id) ?: return Result.failure()
        if (job.status == JobStatus.DONE) return Result.success()

        return try {
            // One job at a time: protects memory and battery on big batches.
            gate.withLock {
                val fresh = dao.get(id) ?: return Result.failure()
                if (fresh.status == JobStatus.CANCELED) return Result.success()
                dao.setStatus(id, JobStatus.RUNNING, JobStage.OCR, null)
                trySetForeground(fresh.name, fresh.processedPages, fresh.pageCount)
                OcrPipeline(applicationContext, dao).run(dao.get(id)!!) { done, total ->
                    trySetForeground(fresh.name, done, total)
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                // User cancel already wrote CANCELED; a system stop (e.g. battery low) just re-queues.
                if (dao.get(id)?.status != JobStatus.CANCELED) dao.setStatus(id, JobStatus.QUEUED, JobStage.OCR, null)
            }
            throw e
        } catch (e: PdfPasswordException) {
            dao.setStatus(id, JobStatus.FAILED, JobStage.OCR, "PASSWORD"); Result.failure()
        } catch (e: PdfInvalidException) {
            dao.setStatus(id, JobStatus.FAILED, JobStage.OCR, "INVALID"); Result.failure()
        } catch (e: Throwable) {
            dao.setStatus(id, JobStatus.FAILED, JobStage.OCR, "OTHER:" + (e.message ?: e.javaClass.simpleName)); Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info("", 0, 0)

    private suspend fun trySetForeground(name: String, done: Int, total: Int) {
        try { setForeground(info(name, done, total)) } catch (_: Exception) { /* not allowed in this state: continue silently */ }
    }

    private fun info(name: String, done: Int, total: Int): ForegroundInfo {
        val n: Notification = NotificationCompat.Builder(applicationContext, OcrApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(applicationContext.getString(R.string.notif_title))
            .setContentText(if (total > 0) "$name  ($done/$total)" else name)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, n)
    }

    companion object {
        const val KEY_ID = "id"
        private const val NOTIF_ID = 4711
        private val gate = Mutex()
    }
}
