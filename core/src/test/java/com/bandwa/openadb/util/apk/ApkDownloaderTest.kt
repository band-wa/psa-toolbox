package com.bandwa.openadb.util.apk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkDownloaderTest {
    @Test
    fun md5Of_matchesKnownDigest() {
        val file = File.createTempFile("apk-downloader", ".bin")
        try {
            file.writeText("hello")
            assertEquals("5d41402abc4b2a76b9719d911017c592", ApkDownloader.md5Of(file))
            assertTrue(ApkDownloader.verifyMd5(file, " 5D41402ABC4B2A76B9719D911017C592\n"))
            assertFalse("空期望值不应通过校验", ApkDownloader.verifyMd5(file, ""))
            assertFalse(ApkDownloader.verifyMd5(file, "deadbeef"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun fileNameFromUrl_stripsQueryAndFallsBack() {
        assertEquals(
            "lasted.apk",
            ApkDownloader.fileNameFromUrl("https://example.com/releases/psaboxlite/lasted.apk?token=1"),
        )
        assertEquals(
            "a_b-c.apk",
            ApkDownloader.fileNameFromUrl("https://host/a_b-c.apk#fragment"),
        )
        assertEquals(
            "update.apk",
            ApkDownloader.fileNameFromUrl("https://host/", fallback = "update.apk"),
        )
        assertEquals(
            "update.apk",
            ApkDownloader.fileNameFromUrl("https://host/下载.apk", fallback = "update.apk"),
        )
    }

    @Test
    fun isInstallerFileName_matchesApkAndPart() {
        assertTrue(ApkDownloader.isInstallerFileName("psaboxlite_update.apk"))
        assertTrue(ApkDownloader.isInstallerFileName("hicar_fix.apk.part"))
        assertFalse(ApkDownloader.isInstallerFileName("wallpaper.jpg"))
        assertFalse(ApkDownloader.isInstallerFileName("scriptFiles"))
    }
}
