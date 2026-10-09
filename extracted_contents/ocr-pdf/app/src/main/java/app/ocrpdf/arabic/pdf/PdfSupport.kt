package app.ocrpdf.arabic.pdf

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PdfPasswordException : Exception("PDF is password protected")
class PdfInvalidException(cause: Throwable? = null) : Exception("Invalid PDF", cause)

object PdfSupport {
    /** Opens with PDFBox using temp-file buffering so large files don't live in the heap. */
    fun loadPdfBox(file: File): PDDocument {
        try {
            val d = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
            if (d.isEncrypted) d.setAllSecurityToBeRemoved(true)
            return d
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw PdfPasswordException()
        } catch (e: java.io.IOException) {
            throw PdfInvalidException(e)
        }
    }

    /** Max bitmap pixels we allow per page, derived from the device's heap budget. */
    fun maxPixels(ctx: Context): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val large = ctx.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
        val mb = if (large) am.largeMemoryClass else am.memoryClass
        return (mb * 40_000L).coerceIn(3_000_000L, 10_000_000L).toInt()
    }

    /** Renders one page white-backed at the given DPI (reduced automatically if it would exceed [maxPixels]). */
    fun render(renderer: PdfRenderer, index: Int, dpi: Int, maxPixels: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val wPt = page.width.toFloat()
            val hPt = page.height.toFloat()
            var scale = dpi / 72f
            val px = (wPt * scale).toDouble() * (hPt * scale)
            if (px > maxPixels) scale = sqrt(maxPixels / (wPt * hPt).toDouble()).toFloat()
            val w = max(1, (wPt * scale).roundToInt())
            val h = max(1, (hPt * scale).roundToInt())
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }
}

/** Detects pages that already carry extractable text (so they don't need OCR). */
class NativeTextProbe(private val doc: PDDocument) {
    private val stripper = PDFTextStripper().apply { setSortByPosition(true) }

    fun text(pageIndex: Int): String {
        stripper.setStartPage(pageIndex + 1)
        stripper.setEndPage(pageIndex + 1)
        return try { stripper.getText(doc).trim() } catch (e: Exception) { "" }
    }

    companion object {
        /** A page counts as "has text" when it contains at least a few letters/digits. */
        fun hasUsableText(t: String): Boolean = t.count { it.isLetterOrDigit() } >= 12
    }
}
