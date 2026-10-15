package com.example.easyreader

import com.example.easyreader.util.ZipHelper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun zipAndUnzip_roundTripSuccess() {
        val srcDir = tempFolder.newFolder("src")
        val file1 = File(srcDir, "hello.txt")
        file1.writeText("Hello EasyReader!")

        val subDir = File(srcDir, "sub")
        subDir.mkdir()
        val file2 = File(subDir, "inner.json")
        file2.writeText("{\"status\":\"ok\"}")

        val zipFile = tempFolder.newFile("output.zip")
        ZipHelper.zip(listOf(file1, subDir), zipFile)
        assertTrue(zipFile.exists() && zipFile.length() > 0)

        val destDir = tempFolder.newFolder("dest")
        ZipHelper.unzip(zipFile, destDir)

        val extracted1 = File(destDir, "hello.txt")
        assertTrue(extracted1.exists())
        assertEquals("Hello EasyReader!", extracted1.readText())

        val extracted2 = File(destDir, "sub/inner.json")
        assertTrue(extracted2.exists())
        assertEquals("{\"status\":\"ok\"}", extracted2.readText())
    }

    @Test
    fun unzip_blocksZipSlipAttack() {
        val maliciousZip = tempFolder.newFile("malicious.zip")
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            val entry = ZipEntry("../malicious.txt")
            zos.putNextEntry(entry)
            zos.write("evil payload".toByteArray())
            zos.closeEntry()
        }

        val targetDir = tempFolder.newFolder("safe_dest")
        try {
            ZipHelper.unzip(maliciousZip, targetDir)
            fail("Expected SecurityException on Zip Slip attempt")
        } catch (_: SecurityException) {
            // Expected
        }
    }
}
