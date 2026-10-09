package app.ocrpdf.arabic.util

import java.util.Locale

object TextUtil {
    /** True when the first strong-directional character is RTL (Arabic/Hebrew). */
    fun isRtl(s: CharSequence): Boolean {
        for (ch in s) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                else -> Unit
            }
        }
        return false
    }

    fun formatBytes(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    }
}
