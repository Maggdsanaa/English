package app.ocrpdf.arabic.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.ocrpdf.arabic.util.TextUtil
import java.io.File

data class PageText(val index: Int, val text: String)

/** Pure text builders (unit-tested). */
object TextExport {
    fun toTxt(pages: List<PageText>): String =
        pages.filter { it.text.isNotBlank() }.joinToString("\n\n") { it.text.trim() } + "\n"

    /**
     * Markdown: one H2 per page. Paragraph lines are re-flowed; RTL paragraphs are wrapped in
     * `<div dir="rtl">` so Arabic renders right-to-left in viewers that allow inline HTML.
     */
    fun toMarkdown(title: String, pages: List<PageText>, pageLabel: (Int) -> String): String {
        val sb = StringBuilder("# ").append(title).append("\n\n")
        for (p in pages) {
            sb.append("## ").append(pageLabel(p.index + 1)).append("\n\n")
            val paras = p.text.trim().split(Regex("\n\\s*\n")).map { it.lines().joinToString(" ") { l -> l.trim() }.trim() }
            for (para in paras.filter { it.isNotEmpty() }) {
                if (TextUtil.isRtl(para)) sb.append("<div dir=\"rtl\">\n\n").append(para).append("\n\n</div>\n\n")
                else sb.append(para).append("\n\n")
            }
        }
        return sb.toString().trimEnd() + "\n"
    }
}

object Sharing {
    private fun uriFor(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    fun shareText(ctx: Context, text: String, fileName: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
        if (text.length <= 50_000) {
            i.putExtra(Intent.EXTRA_TEXT, text)
        } else {   // avoid TransactionTooLargeException: share big text as a file
            val f = cacheCopy(ctx, fileName) { it.writeText(text) }
            i.putExtra(Intent.EXTRA_STREAM, uriFor(ctx, f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            i.clipData = ClipData.newRawUri("", uriFor(ctx, f))
        }
        ctx.startActivity(Intent.createChooser(i, null))
    }

    fun sharePdf(ctx: Context, pdf: File, niceName: String) {
        val f = cacheCopy(ctx, niceName) { pdf.copyTo(it, overwrite = true) }
        val u = uriFor(ctx, f)
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, u)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("", u)
        ctx.startActivity(Intent.createChooser(i, null))
    }

    fun openPdf(ctx: Context, pdf: File, niceName: String) {
        val f = cacheCopy(ctx, niceName) { pdf.copyTo(it, overwrite = true) }
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(ctx, f), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(i)
    }

    fun writeText(ctx: Context, uri: Uri, text: String) {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun copyFile(ctx: Context, from: File, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { o -> from.inputStream().use { it.copyTo(o) } }
    }

    private fun cacheCopy(ctx: Context, name: String, write: (File) -> Unit): File {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return File(dir, safe).also(write)
    }
}
