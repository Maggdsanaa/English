package app.ocrpdf.arabic

import app.ocrpdf.arabic.ocr.Word
import app.ocrpdf.arabic.pdf.TextLayout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TextLayoutTest {
    private val eps = 1e-4f

    /** Applies [a b c d e f] to a point. */
    private fun ap(m: FloatArray, x: Float, y: Float) = floatArrayOf(m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5])

    @Test fun identityForRotation0() {
        assertArrayEquals(floatArrayOf(10f, 20f), ap(TextLayout.displayToUser(0, 0f, 0f, 595f, 842f), 10f, 20f), eps)
    }

    @Test fun rotation90MapsDisplayCornersToUserCorners() {
        // Unrotated page 200 x 100. Displayed (rotated 90 CW) it is 100 x 200.
        val m = TextLayout.displayToUser(90, 0f, 0f, 200f, 100f)
        // display bottom-left (0,0) <-> user bottom-right (200,0)
        assertArrayEquals(floatArrayOf(200f, 0f), ap(m, 0f, 0f), eps)
        // display top-left (0,200) <-> user bottom-left (0,0)
        assertArrayEquals(floatArrayOf(0f, 0f), ap(m, 0f, 200f), eps)
        // display top-right (100,200) <-> user top-left (0,100)
        assertArrayEquals(floatArrayOf(0f, 100f), ap(m, 100f, 200f), eps)
    }

    @Test fun rotation180And270() {
        val m180 = TextLayout.displayToUser(180, 0f, 0f, 200f, 100f)
        assertArrayEquals(floatArrayOf(200f, 100f), ap(m180, 0f, 0f), eps)
        val m270 = TextLayout.displayToUser(270, 0f, 0f, 200f, 100f)
        // display top-left (0,200) <-> user top-right (200,100)
        assertArrayEquals(floatArrayOf(200f, 100f), ap(m270, 0f, 200f), eps)
        // display bottom-left (0,0) <-> user top-left (0,100)
        assertArrayEquals(floatArrayOf(0f, 100f), ap(m270, 0f, 0f), eps)
    }

    @Test fun cropBoxOriginIsApplied() {
        assertArrayEquals(floatArrayOf(15f, 27f), ap(TextLayout.displayToUser(0, 5f, 7f, 100f, 100f), 10f, 20f), eps)
    }

    @Test fun displaySizeSwapsOnQuarterTurns() {
        assertEquals(100f to 200f, TextLayout.displaySize(90, 200f, 100f))
        assertEquals(200f to 100f, TextLayout.displaySize(180, 200f, 100f))
    }

    @Test fun wordPlacementScalesPixelsToPoints() {
        // 1000x2000 px bitmap for a 500x1000 pt page (k = 0.5). Word box: x 100..300, y 200..260 (top-left origin)
        val p = TextLayout.place(Word("hi", 100, 200, 300, 260), 1000, 2000, 500f, 1000f)
        assertEquals(50f, p.x, eps)
        assertEquals(100f, p.widthPt, eps)
        assertEquals(30f, p.heightPt, eps)
        // box bottom is at y=260px -> 130pt from top -> 870pt from bottom (+ small descender allowance)
        assertEquals(870f + 30f * 0.18f, p.baselineY, eps)
    }
}
