package com.example.easyreader.util

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipHelper {
    /**
     * Zips a list of files or directories into a single zip file.
     */
    fun zip(files: List<File>, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            for (file in files) {
                zipEntry(file, "", out)
            }
        }
    }

    private fun zipEntry(file: File, parentPath: String, out: ZipOutputStream) {
        val entryName = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files.isNullOrEmpty()) {
                out.putNextEntry(ZipEntry("$entryName/"))
                out.closeEntry()
            } else {
                for (child in files) {
                    zipEntry(child, entryName, out)
                }
            }
        } else {
            val data = ByteArray(2048)
            FileInputStream(file).use { fi ->
                BufferedInputStream(fi).use { bis ->
                    out.putNextEntry(ZipEntry(entryName))
                    var count: Int
                    while (bis.read(data, 0, 2048).also { count = it } != -1) {
                        out.write(data, 0, count)
                    }
                    out.closeEntry()
                }
            }
        }
    }

    /**
     * Unzips a zip file into a target directory.
     */
    fun unzip(zipFile: File, targetDirectory: File) {
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs()
        }
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(2048)
            while (entry != null) {
                val file = File(targetDirectory, entry.name)
                val canonicalDestPath = file.canonicalPath
                val canonicalDirPath = targetDirectory.canonicalPath
                if (!canonicalDestPath.startsWith(canonicalDirPath + File.separator)) {
                    throw SecurityException("Entry is outside of the target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        var count: Int
                        while (zis.read(buffer).also { count = it } != -1) {
                            fos.write(buffer, 0, count)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
