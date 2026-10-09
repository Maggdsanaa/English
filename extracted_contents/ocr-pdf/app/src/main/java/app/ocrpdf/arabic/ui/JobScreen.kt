package app.ocrpdf.arabic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.ocrpdf.arabic.R
import app.ocrpdf.arabic.data.JobStatus
import app.ocrpdf.arabic.export.Sharing
import app.ocrpdf.arabic.ocr.PageResult
import app.ocrpdf.arabic.util.TextUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JobScreen(vm: MainViewModel, snack: SnackbarHostState, id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val job by vm.job(id).collectAsState(initial = null)
    var pages by remember { mutableStateOf<List<PageResult>>(emptyList()) }
    LaunchedEffect(job?.status) { if (job?.status == JobStatus.DONE) pages = vm.pages(id) }

    val txt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { u ->
        u?.let { vm.saveText(it, vm.fullText(pages)) }
    }
    val md = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { u ->
        u?.let { uri -> job?.let { vm.saveText(uri, vm.markdown(it, pages)) } }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        u?.let { vm.savePdf(it, id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(job?.name ?: "", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val j = job
        if (j == null) { Box(Modifier.padding(pad)) {}; return@Scaffold }
        val base = j.name.removeSuffix(".pdf")
        val niceName = "$base-ocr.pdf"

        Column(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                statusLabel(j) + " · " + stringResource(R.string.pages_fmt, j.processedPages, j.pageCount),
                style = MaterialTheme.typography.titleSmall,
            )
            if (j.status == JobStatus.RUNNING || j.status == JobStatus.QUEUED) {
                LinearProgressIndicator(progress = { j.processedPages / j.pageCount.coerceAtLeast(1).toFloat() }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { vm.cancel(id) }) { Text(stringResource(R.string.cancel)) }
            }
            if (j.status == JobStatus.FAILED) {
                Text(errorLabel(j.error), color = MaterialTheme.colorScheme.error)
            }
            if (j.status == JobStatus.FAILED || j.status == JobStatus.CANCELED) {
                Button(onClick = { vm.resume(id) }) { Text(stringResource(R.string.resume)) }
            }
            if (j.status == JobStatus.DONE) {
                Text(
                    stringResource(R.string.summary_fmt, j.ocrPages, j.nativePages,
                        TextUtil.formatBytes(j.inputBytes), TextUtil.formatBytes(j.outputBytes)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {
                        clipboard.setText(AnnotatedString(vm.fullText(pages)))
                        scope.launch { snack.showSnackbar(ctx.getString(R.string.copied)) }
                    }, label = { Text(stringResource(R.string.copy)) })
                    AssistChip(onClick = { Sharing.shareText(ctx, vm.fullText(pages), "$base.txt") }, label = { Text(stringResource(R.string.share_text)) })
                    AssistChip(onClick = { txt.launch("$base.txt") }, label = { Text(stringResource(R.string.export_txt)) })
                    AssistChip(onClick = { md.launch("$base.md") }, label = { Text(stringResource(R.string.export_md)) })
                    AssistChip(onClick = { pdf.launch(niceName) }, label = { Text(stringResource(R.string.save_pdf)) })
                    AssistChip(onClick = { Sharing.sharePdf(ctx, vm.outputFile(id), niceName) }, label = { Text(stringResource(R.string.share_pdf)) })
                    AssistChip(onClick = { Sharing.openPdf(ctx, vm.outputFile(id), niceName) }, label = { Text(stringResource(R.string.open_pdf)) })
                }
                HorizontalDivider()
                SelectionContainer(Modifier.weight(1f)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(pages, key = { it.index }) { p -> PageText(p) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageText(p: PageResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.page_fmt, p.index + 1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (p.text.isBlank()) {
            Text(stringResource(R.string.no_text), color = MaterialTheme.colorScheme.secondary)
        } else {
            // One Text per paragraph; TextDirection.Content picks RTL for Arabic and LTR for English per paragraph.
            p.text.trim().split(Regex("\n\\s*\n")).forEach { para ->
                Text(
                    para.trim(),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Start),
                )
            }
        }
    }
}
