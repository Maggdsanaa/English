package app.ocrpdf.arabic.work

import android.content.Context
import app.ocrpdf.arabic.ocr.PageResult
import kotlinx.serialization.json.Json
import java.io.File

object JobStore {
    val json = Json { ignoreUnknownKeys = true }

    fun dir(ctx: Context, id: String) = File(ctx.filesDir, "jobs/$id").apply { mkdirs() }
    fun source(ctx: Context, id: String) = File(dir(ctx, id), "source.pdf")
    fun output(ctx: Context, id: String) = File(dir(ctx, id), "output.pdf")
    fun pagesDir(ctx: Context, id: String) = File(dir(ctx, id), "pages").apply { mkdirs() }

    fun checkpoint(pagesDir: File, index: Int) = File(pagesDir, "p%05d.json".format(index))

    fun readPage(pagesDir: File, index: Int): PageResult? = try {
        checkpoint(pagesDir, index).takeIf { it.exists() }?.let { json.decodeFromString<PageResult>(it.readText()) }
    } catch (e: Exception) { null }

    /** Atomic write so a killed process never leaves a half-written checkpoint. */
    fun writePage(pagesDir: File, r: PageResult) {
        val f = checkpoint(pagesDir, r.index)
        val tmp = File(pagesDir, f.name + ".tmp")
        tmp.writeText(json.encodeToString(PageResult.serializer(), r))
        tmp.renameTo(f)
    }

    fun readAllPages(ctx: Context, id: String): List<PageResult> =
        (pagesDir(ctx, id).listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
            .sortedBy { it.name }
            .mapNotNull { runCatching { json.decodeFromString<PageResult>(it.readText()) }.getOrNull() }
}
