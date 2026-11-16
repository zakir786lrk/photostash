package com.photostash.app.core

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

object PolyglotExtractor {

    // Magic bytes for ZIP local file header: PK\x03\x04
    private val ZIP_HEADER_BYTES = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    // Magic bytes for PNG IEND chunk: [0, 0, 0, 0, 'I', 'E', 'N', 'D', 0xAE, 0x42, 0x60, 0x82]
    private val PNG_IEND_BYTES = byteArrayOf(
        0x00, 0x00, 0x00, 0x00,
        0x49, 0x45, 0x4E, 0x44,
        0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
    )

    /**
     * Finds the start offset of the embedded ZIP archive in the image file.
     * Searches for PNG IEND chunk first, then scans for PK\x03\x04 header.
     */
    fun findZipOffset(context: Context, imageUri: Uri): Long {
        // Strategy 1: Look for PNG IEND chunk (PNG polyglots end with IEND chunk followed immediately by ZIP)
        try {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                val bis = BufferedInputStream(stream, 65536)
                val buffer = ByteArray(8192)
                var bytesReadTotal = 0L
                var matchIndex = 0

                while (true) {
                    val bytesRead = bis.read(buffer)
                    if (bytesRead == -1) break

                    for (i in 0 until bytesRead) {
                        if (buffer[i] == PNG_IEND_BYTES[matchIndex]) {
                            matchIndex++
                            if (matchIndex == PNG_IEND_BYTES.size) {
                                val iendOffset = bytesReadTotal + i - (PNG_IEND_BYTES.size - 1)
                                return iendOffset + PNG_IEND_BYTES.size
                            }
                        } else {
                            if (matchIndex > 0) {
                                matchIndex = if (buffer[i] == PNG_IEND_BYTES[0]) 1 else 0
                            }
                        }
                    }
                    bytesReadTotal += bytesRead
                }
            }
        } catch (_: Exception) {}

        // Strategy 2: If no IEND chunk found (e.g. JPEG or modified), scan for PK\x03\x04
        try {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                val bis = BufferedInputStream(stream, 65536)
                val buffer = ByteArray(8192)
                var bytesReadTotal = 0L
                var matchIndex = 0

                while (true) {
                    val bytesRead = bis.read(buffer)
                    if (bytesRead == -1) break

                    for (i in 0 until bytesRead) {
                        if (buffer[i] == ZIP_HEADER_BYTES[matchIndex]) {
                            matchIndex++
                            if (matchIndex == ZIP_HEADER_BYTES.size) {
                                return bytesReadTotal + i - (ZIP_HEADER_BYTES.size - 1)
                            }
                        } else {
                            if (matchIndex > 0) {
                                matchIndex = if (buffer[i] == ZIP_HEADER_BYTES[0]) 1 else 0
                            }
                        }
                    }
                    bytesReadTotal += bytesRead
                }
            }
        } catch (_: Exception) {}

        return -1L
    }

    /**
     * Skips exactly [bytesToSkip] bytes on [stream].
     */
    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                val read = stream.read(buffer, 0, toRead)
                if (read == -1) break
                remaining -= read
            }
        }
    }

    /**
     * Inspect embedded files.
     * First attempts fast parsing of the ZIP Central Directory from the file end.
     * Falls back to sequential ZipInputStream scanning if random access is unavailable.
     */
    suspend fun inspectEmbeddedFiles(
        context: Context,
        imageUri: Uri
    ): List<EmbeddedEntry> = withContext(Dispatchers.IO) {
        val offset = findZipOffset(context, imageUri)
        if (offset < 0) return@withContext emptyList()

        // Attempt 1: Parse Central Directory for accurate metadata and file sizes
        val cdEntries = parseCentralDirectory(context, imageUri)
        if (cdEntries.isNotEmpty()) {
            return@withContext cdEntries
        }

        // Attempt 2: Fallback to ZipInputStream sequential scan
        val entries = mutableListOf<EmbeddedEntry>()
        try {
            context.contentResolver.openInputStream(imageUri)?.use { rawStream ->
                skipFully(rawStream, offset)

                ZipInputStream(BufferedInputStream(rawStream, 65536)).use { zipIn ->
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
        } catch (e: Exception) {
            e.printStackTrace()
        }

        entries
    }

    /**
     * Extracts all hidden files from the image to Downloads/PhotoStash/[timestamp]/.
     */
    suspend fun extractAllFiles(
        context: Context,
        imageUri: Uri,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): List<Uri> = withContext(Dispatchers.IO) {
        val extractedUris = mutableListOf<Uri>()
        val subFolderName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        val offset = findZipOffset(context, imageUri)
        if (offset < 0) return@withContext emptyList()

        context.contentResolver.openInputStream(imageUri)?.use { rawStream ->
            skipFully(rawStream, offset)

            ZipInputStream(BufferedInputStream(rawStream, 65536)).use { zipIn ->
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

    /**
     * Parses the ZIP Central Directory record to get exact file names, sizes, and timestamps.
     */
    private fun parseCentralDirectory(context: Context, imageUri: Uri): List<EmbeddedEntry> {
        val entries = mutableListOf<EmbeddedEntry>()
        try {
            context.contentResolver.openFileDescriptor(imageUri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    val fileSize = channel.size()
                    if (fileSize < 22) return emptyList()

                    val searchSize = minOf(fileSize, 65535L + 22L).toInt()
                    val searchBuffer = ByteBuffer.allocate(searchSize)
                    searchBuffer.order(ByteOrder.LITTLE_ENDIAN)

                    channel.position(fileSize - searchSize)
                    channel.read(searchBuffer)
                    searchBuffer.flip()

                    var eocdPos = -1
                    for (i in (searchSize - 22) downTo 0) {
                        if (searchBuffer.getInt(i) == 0x06054b50) {
                            eocdPos = i
                            break
                        }
                    }
                    if (eocdPos == -1) return emptyList()

                    searchBuffer.position(eocdPos + 10)
                    val totalEntries = searchBuffer.getShort().toInt() and 0xFFFF
                    val cdSize = searchBuffer.getInt().toLong() and 0xFFFFFFFFL
                    val cdOffset = searchBuffer.getInt().toLong() and 0xFFFFFFFFL

                    if (cdSize <= 0 || cdSize > fileSize) return emptyList()

                    val cdFilePosition = (fileSize - searchSize + eocdPos) - cdSize
                    if (cdFilePosition < 0) return emptyList()

                    val cdBuffer = ByteBuffer.allocate(cdSize.toInt())
                    cdBuffer.order(ByteOrder.LITTLE_ENDIAN)
                    channel.position(cdFilePosition)
                    channel.read(cdBuffer)
                    cdBuffer.flip()

                    while (cdBuffer.remaining() >= 46) {
                        val sig = cdBuffer.getInt()
                        if (sig != 0x02014b50) break

                        cdBuffer.position(cdBuffer.position() + 8)
                        val timeShort = cdBuffer.getShort().toInt() and 0xFFFF
                        val dateShort = cdBuffer.getShort().toInt() and 0xFFFF
                        cdBuffer.getInt() // skip CRC

                        val compSize = cdBuffer.getInt().toLong() and 0xFFFFFFFFL
                        val uncompSize = cdBuffer.getInt().toLong() and 0xFFFFFFFFL
                        val nameLen = cdBuffer.getShort().toInt() and 0xFFFF
                        val extraLen = cdBuffer.getShort().toInt() and 0xFFFF
                        val commentLen = cdBuffer.getShort().toInt() and 0xFFFF

                        cdBuffer.position(cdBuffer.position() + 12)

                        if (cdBuffer.remaining() < nameLen) break
                        val nameBytes = ByteArray(nameLen)
                        cdBuffer.get(nameBytes)
                        val fileName = String(nameBytes, Charsets.UTF_8)

                        val skipBytes = extraLen + commentLen
                        if (cdBuffer.remaining() < skipBytes) break
                        cdBuffer.position(cdBuffer.position() + skipBytes)

                        if (!fileName.endsWith("/")) {
                            entries.add(
                                EmbeddedEntry(
                                    name = fileName,
                                    uncompressedSize = uncompSize,
                                    compressedSize = compSize,
                                    timeMillis = dosToJavaTime(dateShort, timeShort)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return entries
    }

    private fun dosToJavaTime(dosDate: Int, dosTime: Int): Long {
        return try {
            val year = ((dosDate shr 9) and 0x7F) + 1980
            val month = ((dosDate shr 5) and 0x0F) - 1
            val day = dosDate and 0x1F
            val hour = (dosTime shr 11) and 0x1F
            val minute = (dosTime shr 5) and 0x3F
            val second = (dosTime and 0x1F) * 2

            val cal = java.util.Calendar.getInstance()
            cal.set(
                year,
                month.coerceIn(0, 11),
                day.coerceIn(1, 31),
                hour.coerceIn(0, 23),
                minute.coerceIn(0, 59),
                second.coerceIn(0, 59)
            )
            cal.timeInMillis
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
