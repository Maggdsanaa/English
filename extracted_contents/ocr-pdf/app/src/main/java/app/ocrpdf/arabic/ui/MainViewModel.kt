package app.ocrpdf.arabic.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.ocrpdf.arabic.OcrApp
import app.ocrpdf.arabic.R
import app.ocrpdf.arabic.data.JobEntity
import app.ocrpdf.arabic.export.Sharing
import app.ocrpdf.arabic.export.PageText
import app.ocrpdf.arabic.export.TextExport
import app.ocrpdf.arabic.ocr.PageResult
import app.ocrpdf.arabic.work.ImportResult
import app.ocrpdf.arabic.work.JobRepository
import app.ocrpdf.arabic.work.JobStore
import app.ocrpdf.arabic.work.OcrOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = JobRepository(app)
    val settings = OcrApp.settings

    val jobs: StateFlow<List<JobEntity>> =
        repo.dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Options chosen on the home screen (initialised from the saved defaults).
    val languages = MutableStateFlow(settings.ocrLang.value)
    val dpi = MutableStateFlow(settings.dpi.value)
    val compression = MutableStateFlow(settings.compression.value)
    val ocrAll = MutableStateFlow(false)

    /** String-resource ids to show as snackbars. */
    val messages = Channel<Int>(Channel.BUFFERED)

    fun job(id: String): Flow<JobEntity?> = repo.dao.observe(id)

    fun import(uris: List<Uri>) {
        val o = OcrOptions(languages.value, dpi.value, compression.value, ocrAll.value)
        viewModelScope.launch {
            for (u in uris) {
                when (repo.import(u, o)) {
                    is ImportResult.Ok -> Unit
                    ImportResult.Password -> messages.send(R.string.err_password)
                    ImportResult.Invalid -> messages.send(R.string.err_invalid)
                }
            }
        }
    }

    fun cancel(id: String) = viewModelScope.launch { repo.cancel(id) }
    fun resume(id: String) = viewModelScope.launch { repo.resume(id) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    suspend fun pages(id: String): List<PageResult> = repo.pages(id)

    fun outputFile(id: String): File = JobStore.output(getApplication(), id)

    fun fullText(pages: List<PageResult>): String = TextExport.toTxt(pages.map { PageText(it.index, it.text) })

    fun markdown(job: JobEntity, pages: List<PageResult>): String {
        val ctx = getApplication<Application>()
        return TextExport.toMarkdown(job.name.removeSuffix(".pdf"), pages.map { PageText(it.index, it.text) }) {
            ctx.getString(R.string.page_fmt, it)
        }
    }

    fun saveText(uri: Uri, text: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { Sharing.writeText(getApplication(), uri, text) }.isSuccess }
        messages.send(if (ok) R.string.saved else R.string.save_failed)
    }

    fun savePdf(uri: Uri, id: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { Sharing.copyFile(getApplication(), outputFile(id), uri) }.isSuccess }
        messages.send(if (ok) R.string.saved else R.string.save_failed)
    }
}
