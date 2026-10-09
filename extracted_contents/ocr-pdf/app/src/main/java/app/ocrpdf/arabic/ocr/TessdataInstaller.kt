package app.ocrpdf.arabic.ocr

import android.content.Context
import java.io.File

/** Copies the bundled ara/eng models from assets into app-private storage (Tesseract needs real files). */
object TessdataInstaller {
    private const val VERSION = 1
    private val LANGS = listOf("ara", "eng")

    /** @return directory that contains the `tessdata` folder (what TessBaseAPI.init expects). */
    @Synchronized
    fun ensure(context: Context): File {
        val root = File(context.filesDir, "tess")
        val td = File(root, "tessdata").apply { mkdirs() }
        val marker = File(root, ".installed_v$VERSION")
        val needCopy = !marker.exists() || LANGS.any { File(td, "$it.traineddata").length() == 0L }
        if (needCopy) {
            for (lang in LANGS) {
                val tmp = File(td, "$lang.traineddata.tmp")
                context.assets.open("tessdata/$lang.traineddata").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                tmp.renameTo(File(td, "$lang.traineddata"))
            }
            marker.writeText("ok")
        }
        return root
    }
}
