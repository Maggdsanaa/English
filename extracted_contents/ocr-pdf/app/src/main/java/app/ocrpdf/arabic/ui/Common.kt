package app.ocrpdf.arabic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ocrpdf.arabic.R
import app.ocrpdf.arabic.data.JobEntity
import app.ocrpdf.arabic.data.JobStage
import app.ocrpdf.arabic.data.JobStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, label) ->
                FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) })
            }
        }
    }
}

@Composable
fun statusLabel(job: JobEntity): String = when (job.status) {
    JobStatus.QUEUED -> stringResource(R.string.status_queued)
    JobStatus.RUNNING -> when (job.stage) {
        JobStage.BUILD -> stringResource(R.string.stage_build)
        JobStage.COMPRESS -> stringResource(R.string.stage_compress)
        JobStage.FINISH -> stringResource(R.string.stage_finish)
        else -> stringResource(R.string.stage_ocr)
    }
    JobStatus.DONE -> stringResource(R.string.status_done)
    JobStatus.CANCELED -> stringResource(R.string.status_canceled)
    else -> stringResource(R.string.status_failed)
}

@Composable
fun errorLabel(code: String?): String = when {
    code == null -> ""
    code == "PASSWORD" -> stringResource(R.string.err_password)
    code == "INVALID" -> stringResource(R.string.err_invalid)
    code.startsWith("OTHER:") -> stringResource(R.string.err_generic, code.removePrefix("OTHER:"))
    else -> code
}
