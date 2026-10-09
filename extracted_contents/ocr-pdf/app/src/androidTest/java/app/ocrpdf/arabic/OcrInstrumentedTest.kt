package app.ocrpdf.arabic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ocrpdf.arabic.data.AppDb
import app.ocrpdf.arabic.data.JobEntity
import app.ocrpdf.arabic.data.JobStage
import app.ocrpdf.arabic.data.JobStatus
import app.ocrpdf.arabic.ocr.OcrEngine
import app.ocrpdf.arabic.ocr.TessdataInstaller
import app.ocrpdf.arabic.pdf.NativeTextProbe
import app.ocrpdf.arabic.pdf.PdfSupport
import app.ocrpdf.arabic.work.JobStore
import app.ocrpdf.arabic.work.OcrPipeline
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** End-to-end tests: run on a device/emulator with `./gradlew connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class OcrInstrumentedTest {
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(ctx)
    }

    private fun textBitmap(text: String, size: Float = 90f): Bitmap {
        val bmp = Bitmap.createBitmap(1600, 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = size }
        c.drawText(text, 60f, 220f, p)
        return bmp
    }

    /** Builds a PDF whose only content is a raster image (i.e. a "scanned" PDF). */
    private fun scannedPdf(file: File, lines: List<String>) {
        val doc = PdfDocument()
        lines.forEachIndexed { i, line ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            val bmp = Bitmap.createBitmap(1190, 1684, Bitmap.Config.ARGB_8888).also { b ->
                val c = Canvas(b); c.drawColor(Color.WHITE)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 80f }
                c.drawText(line, 80f, 300f, p)
            }
            page.canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, 595, 842), null)
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    @Test fun tesseractReadsEnglish() = runBlocking {
        OcrEngine(TessdataInstaller.ensure(ctx), "eng", 300).use { e ->
            val out = e.recognize(textBitmap("Hello OCR World"))
            assertTrue("got: ${out.text}", out.text.contains("Hello", ignoreCase = true))
            assertTrue(out.words.isNotEmpty())
        }
    }

    @Test fun tesseractReadsArabic() = runBlocking {
        OcrEngine(TessdataInstaller.ensure(ctx), "ara+eng", 300).use { e ->
            val out = e.recognize(textBitmap("مرحبا بالعالم"))
            assertTrue("got: ${out.text}", out.text.contains("مرحبا") || out.text.contains("بالعالم"))
        }
    }

    @Test fun fullPipelineProducesSearchablePdf() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).build()
        val dao = db.jobDao()
        val id = UUID.randomUUID().toString()
        scannedPdf(JobStore.source(ctx, id), listOf("Invoice Number 12345", "Second Page Payment"))
        assertFalse(NativeTextProbe.hasUsableText(
            PdfSupport.loadPdfBox(JobStore.source(ctx, id)).use { NativeTextProbe(it).text(0) }))

        val job = JobEntity(id, "scan.pdf", 2, "eng", 1, false, 300, JobStatus.QUEUED, JobStage.OCR,
            0, 0, 0, null, 0L, null, JobStore.source(ctx, id).length(), 0)
        dao.upsert(job)
        OcrPipeline(ctx, dao).run(job)

        val out = JobStore.output(ctx, id)
        assertTrue(out.exists() && out.length() > 0)
        val text = PdfSupport.loadPdfBox(out).use { PDFTextStripper().getText(it) }
        assertTrue("extracted: $text", text.contains("Invoice", ignoreCase = true))
        assertTrue("extracted: $text", text.contains("Payment", ignoreCase = true))
        assertEquals(JobStatus.DONE, dao.get(id)!!.status)

        // Resume: a second run reuses checkpoints and still succeeds.
        OcrPipeline(ctx, dao).run(dao.get(id)!!)
        JobStore.dir(ctx, id).deleteRecursively()
        db.close()
    }
}
