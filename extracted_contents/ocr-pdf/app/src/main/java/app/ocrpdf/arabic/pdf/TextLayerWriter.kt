package app.ocrpdf.arabic.pdf

import android.content.Context
import app.ocrpdf.arabic.ocr.PageResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix

/**
 * Adds an invisible (render mode 3) text layer on top of the ORIGINAL page content, exactly the
 * technique OCRmyPDF uses. The page's images are never touched, so visual quality is preserved.
 * The embedded font (Amiri, OFL) covers Arabic + Latin so extraction/search maps back to Unicode.
 */
class TextLayerWriter(private val doc: PDDocument, context: Context, embedSubset: Boolean) {
    private val font: PDFont by lazy {
        context.assets.open("fonts/Amiri-Regular.ttf").use { PDType0Font.load(doc, it, embedSubset) }
    }
    private val glyphOk = HashMap<Int, Boolean>()

    /** Drops characters the font has no glyph for (encode() throws on those). */
    private fun sanitize(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            val ok = glyphOk.getOrPut(cp) {
                try { font.encode(String(Character.toChars(cp))); true } catch (e: Exception) { false }
            }
            if (ok) sb.appendCodePoint(cp)
        }
        return sb.toString()
    }

    fun addLayer(page: PDPage, r: PageResult) {
        if (r.words.isEmpty() || r.pxW <= 0 || r.pxH <= 0) return
        val crop = page.cropBox
        val wu = crop.width
        val hu = crop.height
        val rot = page.rotation
        val (dispW, dispH) = TextLayout.displaySize(rot, wu, hu)
        val m = TextLayout.displayToUser(rot, crop.lowerLeftX, crop.lowerLeftY, wu, hu)

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.transform(Matrix(m[0], m[1], m[2], m[3], m[4], m[5]))
            cs.beginText()
            cs.setRenderingMode(RenderingMode.NEUTRAL)   // invisible
            for (w in r.words) {
                val txt = sanitize(w.t)
                if (txt.isEmpty()) continue
                val p = TextLayout.place(w, r.pxW, r.pxH, dispW, dispH)
                val natural = try { font.getStringWidth(txt) / 1000f * p.fontSize } catch (e: Exception) { 0f }
                if (natural <= 0f) continue
                val sx = (p.widthPt / natural).coerceIn(0.05f, 10f)   // stretch glyphs to the word's box
                cs.setFont(font, p.fontSize)
                cs.setTextMatrix(Matrix(sx, 0f, 0f, 1f, p.x, p.baselineY))
                cs.showText("$txt ")
            }
            cs.endText()
        }
    }
}
