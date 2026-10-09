package app.ocrpdf.arabic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ocrpdf.arabic.OcrApp
import app.ocrpdf.arabic.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = vm.settings
    val theme by s.theme.collectAsState()
    val lang by s.appLang.collectAsState()
    val ocrLang by s.ocrLang.collectAsState()
    val dpi by s.dpi.collectAsState()
    val comp by s.compression.collectAsState()
    val guard by s.batteryGuard.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChoiceRow(
                stringResource(R.string.theme),
                listOf(0 to stringResource(R.string.theme_system), 1 to stringResource(R.string.theme_light), 2 to stringResource(R.string.theme_dark)),
                theme,
            ) { s.setTheme(it) }
            ChoiceRow(
                stringResource(R.string.app_language),
                listOf("" to stringResource(R.string.theme_system), "ar" to "العربية", "en" to "English"),
                lang,
            ) { s.setAppLang(it); OcrApp.applyAppLanguage(it) }

            Text(stringResource(R.string.defaults_title), style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                stringResource(R.string.language),
                listOf("ara" to stringResource(R.string.lang_ar), "eng" to stringResource(R.string.lang_en), "ara+eng" to stringResource(R.string.lang_both)),
                ocrLang,
            ) { s.setOcrLang(it); vm.languages.value = it }
            ChoiceRow(stringResource(R.string.quality), listOf(200 to "200", 300 to "300"), dpi) { s.setDpi(it); vm.dpi.value = it }
            ChoiceRow(
                stringResource(R.string.compression),
                listOf(0 to stringResource(R.string.comp_off), 1 to stringResource(R.string.comp_balanced), 2 to stringResource(R.string.comp_strong)),
                comp,
            ) { s.setCompression(it); vm.compression.value = it }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.battery_guard), Modifier.weight(1f))
                Switch(checked = guard, onCheckedChange = { s.setBatteryGuard(it) })
            }
            Text(stringResource(R.string.about_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
