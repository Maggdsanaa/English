package app.ocrpdf.arabic.ocr

import kotlinx.serialization.Serializable

/** One recognized word. Coordinates are bitmap pixels, origin top-left. */
@Serializable
data class Word(val t: String, val l: Int, val tp: Int, val r: Int, val b: Int, val c: Int = 0)

/** Per-page checkpoint, persisted as JSON so work can be resumed. */
@Serializable
data class PageResult(
    val index: Int,
    val source: String,          // "ocr" | "native" | "empty"
    val text: String,
    val words: List<Word> = emptyList(),
    val pxW: Int = 0,
    val pxH: Int = 0,
)

object PageSource {
    const val OCR = "ocr"
    const val NATIVE = "native"
    const val EMPTY = "empty"
}
