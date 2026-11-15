package com.photostash.app.core

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

object PolyglotExtractor {

    // Magic bytes for ZIP local file header: PK\x03\x04
    private val ZIP_HEADER_BYTES = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())

    // Magic bytes for PNG IEND chunk: [0, 0, 0, 0, 'I', 'E', 'N', 'D', 0xAE, 0x42, 0x60, 0x82]
    private val PNG_IEND_BYTES = byteArrayOf(
        0x00, 0x00, 0x00, 0x00,
        0x49, 0x45, 0x4E, 0x44,
        0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
    )

    private fun findZipOffset(inputStream: InputStream): Long {
        val bis = BufferedInputStream(inputStream, 65536)
        bis.mark(10 * 1024 * 1024) // up to 10MB mark

        var bytesReadTotal = 0L
        val buffer = ByteArray(8192)
        var matchIndex = 0

        while (true) {
            val bytesRead = bis.read(buffer)
            if (bytesRead == -1) break

            for (i in 0 until bytesRead) {
                if (buffer[i] == ZIP_HEADER_BYTES[matchIndex]) {
                    matchIndex++
                    if (matchIndex == ZIP_HEADER_BYTES.size) {
                        val offset = bytesReadTotal + i - (ZIP_HEADER_BYTES.size - 1)
                        bis.reset()
                        return offset
                    }
                } else {
                    if (matchIndex > 0) {
                        // Check if current byte matches first byte
                        matchIndex = if (buffer[i] == ZIP_HEADER_BYTES[0]) 1 else 0
                    }
                }
            }
            bytesReadTotal += bytesRead
        }

        bis.reset()
        return -1L
    }

    suspend fun inspectEmbeddedFiles(
        context: Context,
        imageUri: Uri
    ): List<EmbeddedEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<EmbeddedEntry>()

        context.contentResolver.openInputStream(imageUri)?.use { rawStream ->
            val offset = findZipOffset(rawStream)
            if (offset < 0) return@withContext emptyList()

            // Skip to start of ZIP
            var skipped = 0L
            while (skipped < offset) {
                val s = rawStream.skip(offset - skipped)
                if (s <= 0) break
                skipped += s
            }

            ZipInputStream(rawStream).use { zipIn ->
                while (true) {
                    val entry = zipIn.nextEntry ?: break
                    if (!entry.isDirectory) {
                        entries.add(
                            EmbeddedEntry(
                                name = entry.name,
                                uncompressedSize = if (entry.size >= 0) entry.size else 0L,
                                compressedSize = if (entry.compressedSize >= 0) entry.compressedSize else 0L,
                                timeMillis = if (entry.time > 0) entry.time else System.currentTimeMillis()
                            )
                        )
                    }
                    zipIn.closeEntry()
                }
            }
        }

        entries
    }

    suspend fun extractAllFiles(
        context: Context,
        imageUri: Uri,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): List<Uri> = withContext(Dispatchers.IO) {
        val extractedUris = mutableListOf<Uri>()
        val subFolderName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        context.contentResolver.openInputStream(imageUri)?.use { rawStream ->
            val offset = findZipOffset(rawStream)
            if (offset < 0) return@withContext emptyList()

            var skipped = 0L
            while (skipped < offset) {
                val s = rawStream.skip(offset - skipped)
                if (s <= 0) break
                skipped += s
            }

            ZipInputStream(rawStream).use { zipIn ->
                val buffer = ByteArray(8192)
                var count = 0

                while (true) {
                    val entry = zipIn.nextEntry ?: break
                    if (!entry.isDirectory) {
                        count++
                        onProgress(0.5f, "Extracting ${entry.name}...")

                        val savedUri = FileUtils.saveExtractedFileToDownloads(
                            context = context,
                            subFolder = subFolderName,
                            fileName = entry.name
                        ) { outputStream ->
                            var len: Int
                            while (zipIn.read(buffer).also { len = it } != -1) {
                                outputStream.write(buffer, 0, len)
                            }
                        }

                        if (savedUri != null) {
                            extractedUris.add(savedUri)
                        }
                    }
                    zipIn.closeEntry()
                }
            }
        }

        onProgress(1.0f, "Extraction complete!")
        extractedUris
    }
}
