package app.ocrpdf.arabic.work

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import app.ocrpdf.arabic.data.JobDao
import app.ocrpdf.arabic.data.JobEntity
import app.ocrpdf.arabic.data.JobStage
import app.ocrpdf.arabic.ocr.*
import app.ocrpdf.arabic.pdf.*
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File

/**
 * Detect scanned pages -> rasterize -> Tesseract -> checkpoint each page -> assemble a searchable PDF
 * (original pages + invisible text layer), optionally compress. Every page is checkpointed to disk so
 * an interrupted job resumes where it stopped.
 */
class OcrPipeline(private val ctx: Context, private val dao: JobDao) {

    suspend fun run(job: JobEntity, onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }) {
        val id = job.id
        val src = JobStore.source(ctx, id)
        val pagesDir = JobStore.pagesDir(ctx, id)
        val total = job.pageCount
        val maxPx = PdfSupport.maxPixels(ctx)

        var ocrPages = 0
        var nativePages = 0

        val probeDoc = PdfSupport.loadPdfBox(src)
        try {
            val probe = NativeTextProbe(probeDoc)
            val pfd = try { ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY) }
                      catch (e: Exception) { throw PdfInvalidException(e) }
            pfd.use {
                val renderer = try { PdfRenderer(it) }
                    catch (e: SecurityException) { throw PdfPasswordException() }
                    catch (e: java.io.IOException) { throw PdfInvalidException(e) }
                renderer.use { rd ->
                    var engine: OcrEngine? = null
                    try {
                        for (i in 0 until total) {
                            coroutineContext.ensureActive()
                            val res = JobStore.readPage(pagesDir, i) ?: run {
                                val nativeText = probe.text(i)
                                val r = if (NativeTextProbe.hasUsableText(nativeText) && !job.ocrAll) {
                                    PageResult(i, PageSource.NATIVE, nativeText)
                                } else {
                                    if (engine == null) {
                                        engine = OcrEngine(TessdataInstaller.ensure(ctx), job.languages, job.dpi)
                                    }
                                    val bmp = PdfSupport.render(rd, i, job.dpi, maxPx)
                                    try {
                                        val out = engine!!.recognize(bmp)
                                        PageResult(
                                            i, if (out.text.isBlank()) PageSource.EMPTY else PageSource.OCR,
                                            out.text, out.words, bmp.width, bmp.height,
                                        )
                                    } finally { bmp.recycle() }
                                }
                                coroutineContext.ensureActive()
                                JobStore.writePage(pagesDir, r)
                                r
                            }
                            if (res.source == PageSource.NATIVE) nativePages++ else if (res.source == PageSource.OCR) ocrPages++
                            dao.progress(id, i + 1, ocrPages, nativePages, JobStage.OCR)
                            onProgress(i + 1, total)
                        }
                    } finally { engine?.close() }
                }
            }
        } finally { probeDoc.close() }

        coroutineContext.ensureActive()
        val results = (0 until total).mapNotNull { JobStore.readPage(pagesDir, it) }
        dao.progress(id, total, ocrPages, nativePages, JobStage.BUILD)

        val out = JobStore.output(ctx, id)
        try {
            assemble(job, results, src, out, embedSubset = true)
        } catch (e: PdfPasswordException) { throw e
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            // Font subsetting can fail on exotic devices/fonts: retry with the full font embedded.
            assemble(job, results, src, out, embedSubset = false)
        }
        dao.markDone(id, System.currentTimeMillis(), out.length())
    }

    private suspend fun assemble(job: JobEntity, results: List<PageResult>, src: File, out: File, embedSubset: Boolean) {
        val tmp = File(out.parentFile, "output.tmp.pdf")
        PdfSupport.loadPdfBox(src).use { doc ->
            if (job.compression > 0) {
                dao.progress(job.id, job.pageCount, job.ocrPages, job.nativePages, JobStage.COMPRESS)
                PdfCompressor.compress(doc, job.compression)
            }
            dao.progress(job.id, job.pageCount, job.ocrPages, job.nativePages, JobStage.BUILD)
            val writer = TextLayerWriter(doc, ctx, embedSubset)
            for (r in results) {
                coroutineContext.ensureActive()
                if (r.source == PageSource.OCR) writer.addLayer(doc.getPage(r.index), r)
            }
            doc.save(tmp)
        }
        if (out.exists()) out.delete()
        check(tmp.renameTo(out)) { "Could not finalize output file" }
    }
}
