package app.ocrpdf.arabic.ocr

import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Thin, cancellable wrapper around Tesseract (LSTM engine). One instance = one language set. */
class OcrEngine(dataRoot: File, languages: String, dpi: Int) : AutoCloseable {
    data class Output(val text: String, val words: List<Word>)

    private val api = TessBaseAPI()

    init {
        val ok = api.init(dataRoot.absolutePath, languages, TessBaseAPI.OEM_LSTM_ONLY)
        if (!ok) {
            api.recycle()
            throw IllegalStateException("Tesseract init failed for '$languages'")
        }
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        api.setVariable("user_defined_dpi", dpi.toString())
        api.setVariable("preserve_interword_spaces", "1")
    }

    /** Recognizes one page. Cancelling the calling coroutine stops Tesseract mid-page. */
    suspend fun recognize(bitmap: Bitmap): Output = withContext(Dispatchers.Default) {
        val finished = AtomicBoolean(false)
        val watcher = launch {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                if (!finished.get()) api.stop()
                throw e
            }
        }
        try {
            doRecognize(bitmap)
        } finally {
            finished.set(true)
            watcher.cancel()
        }
    }

    private fun doRecognize(bitmap: Bitmap): Output {
        api.setImage(bitmap)
        val text = api.getUTF8Text() ?: ""        // triggers recognition
        val words = ArrayList<Word>()
        val it = api.getResultIterator()
        if (it != null) {
            val level = TessBaseAPI.PageIteratorLevel.RIL_WORD
            it.begin()
            do {
                val w = it.getUTF8Text(level)?.trim()
                if (!w.isNullOrEmpty()) {
                    val r = it.getBoundingRect(level)
                    words += Word(w, r.left, r.top, r.right, r.bottom, it.confidence(level).toInt())
                }
            } while (it.next(level))
            it.delete()
        }
        api.clear()
        return Output(text.trim(), words)
    }

    override fun close() {
        api.recycle()
    }
}
