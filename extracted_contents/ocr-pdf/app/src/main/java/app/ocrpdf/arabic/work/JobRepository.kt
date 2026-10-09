package app.ocrpdf.arabic.work

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.work.*
import app.ocrpdf.arabic.OcrApp
import app.ocrpdf.arabic.data.*
import app.ocrpdf.arabic.ocr.PageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class OcrOptions(val languages: String, val dpi: Int, val compression: Int, val ocrAll: Boolean)

sealed interface ImportResult {
    data class Ok(val id: String) : ImportResult
    object Password : ImportResult
    object Invalid : ImportResult
}

class JobRepository(private val ctx: Context) {
    private val db = AppDb.get(ctx)
    val dao: JobDao get() = db.jobDao()
    private val wm get() = WorkManager.getInstance(ctx)

    /** Copies the picked PDF into private storage (SAF URIs can expire), validates it, queues OCR. */
    suspend fun import(uri: Uri, o: OcrOptions): ImportResult = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val src = JobStore.source(ctx, id)
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { i -> src.outputStream().use { out -> i.copyTo(out) } }
            val pages = ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                try { PdfRenderer(pfd).use { it.pageCount } }
                catch (e: SecurityException) { JobStore.dir(ctx, id).deleteRecursively(); return@withContext ImportResult.Password }
            }
            if (pages <= 0) { JobStore.dir(ctx, id).deleteRecursively(); return@withContext ImportResult.Invalid }
            dao.upsert(
                JobEntity(
                    id, displayName(uri), pages, o.languages, o.compression, o.ocrAll, o.dpi,
                    JobStatus.QUEUED, JobStage.OCR, 0, 0, 0, null, System.currentTimeMillis(), null, src.length(), 0,
                )
            )
            enqueue(id)
            ImportResult.Ok(id)
        } catch (e: Exception) {
            JobStore.dir(ctx, id).deleteRecursively()
            ImportResult.Invalid
        }
    }

    fun enqueue(id: String) {
        val builder = Constraints.Builder()
        if (OcrApp.settings.batteryGuard.value) builder.setRequiresBatteryNotLow(true)
        val req = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(workDataOf(OcrWorker.KEY_ID to id))
            .setConstraints(builder.build())
            .addTag(id)
            .build()
        wm.enqueueUniqueWork("job-$id", ExistingWorkPolicy.REPLACE, req)
    }

    suspend fun cancel(id: String) {
        dao.setStatus(id, JobStatus.CANCELED, JobStage.OCR, null)   // written first so the worker knows it was the user
        wm.cancelUniqueWork("job-$id")
    }

    /** Continue a failed/canceled job: already-checkpointed pages are skipped. */
    suspend fun resume(id: String) {
        dao.setStatus(id, JobStatus.QUEUED, JobStage.OCR, null)
        enqueue(id)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        wm.cancelUniqueWork("job-$id")
        dao.delete(id)
        JobStore.dir(ctx, id).deleteRecursively()
    }

    suspend fun pages(id: String): List<PageResult> = withContext(Dispatchers.IO) { JobStore.readAllPages(ctx, id) }

    private fun displayName(uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { return it }
        }
        return uri.lastPathSegment ?: "document.pdf"
    }
}
