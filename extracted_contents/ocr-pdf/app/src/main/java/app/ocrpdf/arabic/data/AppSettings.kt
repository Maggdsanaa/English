package app.ocrpdf.arabic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Tiny SharedPreferences-backed settings holder exposing StateFlows. */
class AppSettings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(p.getInt("theme", 0))            // 0 system, 1 light, 2 dark
    private val _appLang = MutableStateFlow(p.getString("appLang", "") ?: "") // "", "ar", "en"
    private val _ocrLang = MutableStateFlow(p.getString("ocrLang", "ara+eng") ?: "ara+eng")
    private val _dpi = MutableStateFlow(p.getInt("dpi", 300))
    private val _compression = MutableStateFlow(p.getInt("compression", 0))
    private val _batteryGuard = MutableStateFlow(p.getBoolean("batteryGuard", true))

    val theme: StateFlow<Int> = _theme
    val appLang: StateFlow<String> = _appLang
    val ocrLang: StateFlow<String> = _ocrLang
    val dpi: StateFlow<Int> = _dpi
    val compression: StateFlow<Int> = _compression
    val batteryGuard: StateFlow<Boolean> = _batteryGuard

    fun setTheme(v: Int) { _theme.value = v; p.edit().putInt("theme", v).apply() }
    fun setAppLang(v: String) { _appLang.value = v; p.edit().putString("appLang", v).apply() }
    fun setOcrLang(v: String) { _ocrLang.value = v; p.edit().putString("ocrLang", v).apply() }
    fun setDpi(v: Int) { _dpi.value = v; p.edit().putInt("dpi", v).apply() }
    fun setCompression(v: Int) { _compression.value = v; p.edit().putInt("compression", v).apply() }
    fun setBatteryGuard(v: Boolean) { _batteryGuard.value = v; p.edit().putBoolean("batteryGuard", v).apply() }
}
