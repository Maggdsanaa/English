package app.ocrpdf.arabic.pdf

import app.ocrpdf.arabic.ocr.Word

/** Pure geometry helpers for the invisible text layer (unit-tested, no Android deps). */
object TextLayout {
    /**
     * Matrix [a b c d e f] mapping "display space" (points, origin bottom-left of the page as displayed,
     * i.e. after /Rotate) into the page's user space. [wu]/[hu] are the unrotated crop-box size.
     */
    fun displayToUser(rotation: Int, llx: Float, lly: Float, wu: Float, hu: Float): FloatArray {
        val m = when (((rotation % 360) + 360) % 360) {
            90 -> floatArrayOf(0f, 1f, -1f, 0f, wu, 0f)
            180 -> floatArrayOf(-1f, 0f, 0f, -1f, wu, hu)
            270 -> floatArrayOf(0f, -1f, 1f, 0f, 0f, hu)
            else -> floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        }
        m[4] += llx
        m[5] += lly
        return m
    }

    fun displaySize(rotation: Int, wu: Float, hu: Float): Pair<Float, Float> =
        if (((rotation % 360) + 360) % 360 % 180 == 90) hu to wu else wu to hu

    data class Placement(val x: Float, val baselineY: Float, val widthPt: Float, val heightPt: Float, val fontSize: Float)

    /** Converts a word box in bitmap pixels into display-space points. */
    fun place(w: Word, pxW: Int, pxH: Int, dispW: Float, dispH: Float): Placement {
        val kx = dispW / pxW
        val ky = dispH / pxH
        val hPt = ((w.b - w.tp) * ky).coerceAtLeast(1f)
        val wPt = ((w.r - w.l) * kx).coerceAtLeast(1f)
        return Placement(
            x = w.l * kx,
            baselineY = dispH - w.b * ky + hPt * 0.18f,
            widthPt = wPt,
            heightPt = hPt,
            fontSize = (hPt * 0.85f).coerceIn(2f, 120f),
        )
    }
}
