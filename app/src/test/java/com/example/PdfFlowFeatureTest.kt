package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.localization.AppLanguage
import com.example.localization.stringsFor
import com.example.pdf.CompressionLevel
import com.example.pdf.PdfConverterEngine
import com.example.pdf.PdfEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PdfFlowFeatureTest {

    @Test
    fun allTenLanguagesProvideCompleteTranslationsAndRtlFlags() {
        assertEquals(10, AppLanguage.entries.size)
        AppLanguage.entries.forEach { lang ->
            val s = stringsFor(lang)
            assertTrue("Missing appName for ${lang.code}", s.appName.isNotBlank())
            assertTrue("Missing navHome for ${lang.code}", s.navHome.isNotBlank())
            assertTrue("Missing guestWarning for ${lang.code}", s.guestWarning.isNotBlank())
            assertTrue("Missing saveAndDownload for ${lang.code}", s.saveAndDownload.isNotBlank())
        }
        assertTrue(AppLanguage.ARABIC.isRtl)
        assertTrue(AppLanguage.URDU.isRtl)
        assertFalse(AppLanguage.ENGLISH.isRtl)
        assertFalse(AppLanguage.BENGALI.isRtl)
    }

    @Test
    fun createTextPdfAndReadMetadataAndMergeWorks() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PdfEngine.ensureInit(context)
        val dir = context.cacheDir
        val pdf1 = File(dir, "test_doc_1.pdf")
        val pdf2 = File(dir, "test_doc_2.pdf")
        val merged = File(dir, "test_merged.pdf")

        PdfConverterEngine.textToPdf("PDF Flow Alpha", "First page body content for PDF Flow testing.", pdf1)
        PdfConverterEngine.textToPdf("PDF Flow Beta", "Second page body content for merging.", pdf2)

        assertTrue(pdf1.exists() && pdf1.length() > 100L)
        assertEquals(1, PdfEngine.getPageCount(context, pdf1))

        PdfEngine.mergePdfs(context, listOf(pdf1, pdf2), merged)
        assertTrue(merged.exists())
        assertEquals(2, PdfEngine.getPageCount(context, merged))

        val compressed = File(dir, "test_compressed.pdf")
        val compRes = PdfEngine.compressPdf(context, merged, CompressionLevel.MEDIUM, 60, compressed)
        assertTrue(compRes.outputFile.exists() && compRes.compressedBytes > 0L)

        val protectedFile = File(dir, "test_protected.pdf")
        PdfEngine.protectPdf(context, merged, "secret123", "owner123", true, false, false, protectedFile)
        assertTrue(protectedFile.exists())
        val unlockedFile = File(dir, "test_unlocked.pdf")
        PdfEngine.unlockPdf(context, protectedFile, "secret123", unlockedFile)
        assertEquals(2, PdfEngine.getPageCount(context, unlockedFile))

        pdf1.delete()
        pdf2.delete()
        merged.delete()
        compressed.delete()
        protectedFile.delete()
        unlockedFile.delete()
    }
}
