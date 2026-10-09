package app.ocrpdf.arabic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ocrpdf.arabic.R
import app.ocrpdf.arabic.data.JobEntity
import app.ocrpdf.arabic.data.JobStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, snack: SnackbarHostState, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val jobs by vm.jobs.collectAsState()
    val langs by vm.languages.collectAsState()
    val dpi by vm.dpi.collectAsState()
    val comp by vm.compression.collectAsState()
    val all by vm.ocrAll.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.import(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.offline_note), style = MaterialTheme.typography.bodyMedium)
                        ChoiceRow(
                            stringResource(R.string.language),
                            listOf("ara" to stringResource(R.string.lang_ar), "eng" to stringResource(R.string.lang_en),
                                "ara+eng" to stringResource(R.string.lang_both)),
                            langs,
                        ) { vm.languages.value = it }
                        ChoiceRow(stringResource(R.string.quality), listOf(200 to "200", 300 to "300"), dpi) { vm.dpi.value = it }
                        ChoiceRow(
                            stringResource(R.string.compression),
                            listOf(0 to stringResource(R.string.comp_off), 1 to stringResource(R.string.comp_balanced),
                                2 to stringResource(R.string.comp_strong)),
                            comp,
                        ) { vm.compression.value = it }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ocr_all), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = all, onCheckedChange = { vm.ocrAll.value = it })
                        }
                        Button(
                            onClick = { picker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.pick_pdfs)) }
                    }
                }
            }
            item { Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium) }
            if (jobs.isEmpty()) {
                item { Text(stringResource(R.string.empty_history), color = MaterialTheme.colorScheme.secondary) }
            }
            items(jobs, key = { it.id }) { job -> JobCard(job, vm, onOpen) }
        }
    }
}

@Composable
private fun JobCard(job: JobEntity, vm: MainViewModel, onOpen: (String) -> Unit) {
    val active = job.status == JobStatus.RUNNING || job.status == JobStatus.QUEUED
    Card(Modifier.fillMaxWidth().clickable { onOpen(job.id) }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(job.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                statusLabel(job) + " · " + stringResource(R.string.pages_fmt, job.processedPages, job.pageCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
            )
            if (active) {
                LinearProgressIndicator(
                    progress = { if (job.pageCount == 0) 0f else job.processedPages / job.pageCount.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (job.status == JobStatus.FAILED) {
                Text(errorLabel(job.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (active) TextButton(onClick = { vm.cancel(job.id) }) { Text(stringResource(R.string.cancel)) }
                if (job.status == JobStatus.FAILED || job.status == JobStatus.CANCELED)
                    TextButton(onClick = { vm.resume(job.id) }) { Text(stringResource(R.string.resume)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.delete(job.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
            }
        }
    }
}
