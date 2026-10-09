package app.ocrpdf.arabic.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Optional, conservative image recompression. Only large 8-bit colour/gray images WITHOUT masks are
 * re-encoded as JPEG (and only if the result is clearly smaller). 1-bit scans (CCITT/JBIG2) and
 * anything with masks/soft-masks are left untouched so text edges stay crisp.
 */
object PdfCompressor {
    fun compress(doc: PDDocument, level: Int, onPage: (Int) -> Unit = {}) {
        if (level <= 0) return
        val quality = if (level == 1) 0.75f else 0.60f
        val cap = if (level == 1) 2200 else 1600
        val cache = IdentityHashMap<COSStream, PDImageXObject?>()
        for ((pi, page) in doc.pages.withIndex()) {
            onPage(pi)
            val res = page.resources ?: continue
            for (name in res.xObjectNames.toList()) {
                val x = try { res.getXObject(name) } catch (e: Exception) { null }
                if (x !is PDImageXObject) continue
                val key = x.cosObject
                if (!cache.containsKey(key)) cache[key] = recompress(doc, x, quality, cap)
                cache[key]?.let { res.put(name, it) }
            }
        }
    }

    private fun recompress(doc: PDDocument, x: PDImageXObject, quality: Float, cap: Int): PDImageXObject? {
        try {
            if (x.isStencil || x.mask != null || x.softMask != null) return null
            if (x.bitsPerComponent != 8) return null
            if (x.colorSpace.numberOfComponents !in 1..3 || x.colorSpace.numberOfComponents == 2) return null
            val oldLen = x.cosObject.length.toLong()
            val longEdge = max(x.width, x.height)
            if (longEdge <= cap && oldLen < 200_000L) return null

            val src = x.image ?: return null
            var work: Bitmap = src
            if (longEdge > cap) {
                val s = cap.toFloat() / longEdge
                work = Bitmap.createScaledBitmap(src, (src.width * s).roundToInt().coerceAtLeast(1),
                    (src.height * s).roundToInt().coerceAtLeast(1), true)
            }
            val img = JPEGFactory.createFromImage(doc, work, quality)
            if (work !== src) work.recycle()
            src.recycle()
            return if (img.cosObject.length.toLong() < oldLen * 0.9) img else null
        } catch (e: Throwable) {   // includes OutOfMemoryError: skip this image, keep the original
            return null
        }
    }
}
