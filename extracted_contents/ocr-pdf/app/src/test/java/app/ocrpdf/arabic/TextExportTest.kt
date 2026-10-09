package app.ocrpdf.arabic

import app.ocrpdf.arabic.export.PageText
import app.ocrpdf.arabic.export.TextExport
import app.ocrpdf.arabic.util.TextUtil
import org.junit.Assert.*
import org.junit.Test

class TextExportTest {
    @Test fun rtlDetectionUsesFirstStrongChar() {
        assertTrue(TextUtil.isRtl("مرحبا بالعالم"))
        assertFalse(TextUtil.isRtl("Hello world"))
        assertTrue(TextUtil.isRtl("123 - مرحبا Hello"))
        assertFalse(TextUtil.isRtl("Hello مرحبا"))
        assertFalse(TextUtil.isRtl("12345"))
    }

    @Test fun txtSkipsBlankPagesAndJoinsWithBlankLine() {
        val t = TextExport.toTxt(listOf(PageText(0, "one\ntwo"), PageText(1, "  "), PageText(2, "three")))
        assertEquals("one\ntwo\n\nthree\n", t)
    }

    @Test fun markdownHasPageHeadingsAndRtlWrapper() {
        val md = TextExport.toMarkdown(
            "Doc",
            listOf(PageText(0, "Hello\nworld\n\nSecond"), PageText(1, "مرحبا\nبالعالم")),
        ) { "Page $it" }
        assertTrue(md.startsWith("# Doc\n\n## Page 1\n\nHello world\n\nSecond\n\n## Page 2"))
        assertTrue(md.contains("<div dir=\"rtl\">\n\nمرحبا بالعالم\n\n</div>"))
    }
}
