package com.photostash.app.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PolyglotPacker {

    suspend fun packFilesIntoImage(
        context: Context,
        title: String,
        outputFileName: String,
        files: List<FileInfo>,
        coverBitmap: Bitmap? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Uri? = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext null

        val cleanTitle = title.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "PhotoStash" }
        val baseName = outputFileName.trim().removeSuffix(".png").removeSuffix(".PNG").ifBlank { "Stash" }
        val finalFileName = if (baseName.startsWith(cleanTitle, ignoreCase = true)) {
            "$baseName.png"
        } else {
            "${cleanTitle}_$baseName.png"
        }

        val captionMetadata = buildCaptionMetadata(title, files)

        onProgress(0.1f, "Generating visual index card...")
        val bitmap = coverBitmap ?: VisualCardGenerator.generateCoverBitmap(
            title = title,
            files = files
        )

        // 1. Compress bitmap to PNG byte array
        onProgress(0.3f, "Encoding PNG image...")
        val rawPngBytes = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }

        // Insert PNG tEXt chunks (Description and Comment) right before IEND chunk
        val pngBytesWithMetadata = injectPngMetadataChunks(rawPngBytes, captionMetadata)

        // 2. Prepare destination image in MediaStore with description metadata
        onProgress(0.5f, "Creating output image...")
        val savedUri = FileUtils.saveImageToMediaStore(
            context = context,
            displayName = finalFileName,
            title = title,
            description = captionMetadata
        ) { rawOutputStream ->
            BufferedOutputStream(rawOutputStream).use { bufferedOut ->
                // First: Write PNG binary bytes (including PNG header, tEXt chunks, and IEND chunk)
                bufferedOut.write(pngBytesWithMetadata)
                bufferedOut.flush()

                // Second: Append ZIP archive with the user files
                onProgress(0.7f, "Compressing and embedding files...")
                val zipOut = ZipOutputStream(bufferedOut).apply {
                    setLevel(Deflater.BEST_COMPRESSION)
                }

                val buffer = ByteArray(8192)
                val usedNames = mutableSetOf<String>()

                files.forEachIndexed { index, fileInfo ->
                    onProgress(
                        0.7f + (0.25f * (index.toFloat() / files.size.toFloat())),
                        "Embedding ${fileInfo.name}..."
                    )

                    var entryName = fileInfo.name
                    var counter = 1
                    while (usedNames.contains(entryName)) {
                        val base = fileInfo.name.substringBeforeLast('.', "")
                        val ext = fileInfo.name.substringAfterLast('.', "")
                        entryName = if (ext.isNotEmpty()) "$base ($counter).$ext" else "$base ($counter)"
                        counter++
                    }
                    usedNames.add(entryName)

                    val zipEntry = ZipEntry(entryName).apply {
                        time = System.currentTimeMillis()
                    }
                    zipOut.putNextEntry(zipEntry)

                    context.contentResolver.openInputStream(fileInfo.uri)?.use { inStream ->
                        var bytesRead: Int
                        while (inStream.read(buffer).also { bytesRead = it } != -1) {
                            zipOut.write(buffer, 0, bytesRead)
                        }
                    }
                    zipOut.closeEntry()
                }

                zipOut.finish()
                zipOut.flush()
                bufferedOut.flush()
            }
        }

        onProgress(1.0f, "Done!")
        savedUri
    }

    /**
     * Builds detailed text metadata with archive name, file count, total size, and full file list.
     */
    fun buildCaptionMetadata(title: String, files: List<FileInfo>): String {
        val totalSize = FileUtils.formatFileSize(files.sumOf { it.size })
        val sb = StringBuilder()
        sb.append("PhotoStash Archive: ").append(title).append("\n")
        sb.append("${files.size} Files • Total: $totalSize\n\n")
        sb.append("Embedded Files:\n")
        files.forEachIndexed { index, file ->
            sb.append("${index + 1}. ${file.name} (${file.formattedSize})\n")
        }
        return sb.toString().trim()
    }

    /**
     * Injects PNG tEXt chunks before the final 12-byte IEND chunk.
     */
    private fun injectPngMetadataChunks(pngBytes: ByteArray, description: String): ByteArray {
        val iendSize = 12
        if (pngBytes.size <= iendSize) return pngBytes

        val descChunk = createPngTextChunk("Description", description)
        val commentChunk = createPngTextChunk("Comment", description)

        val beforeIend = pngBytes.copyOfRange(0, pngBytes.size - iendSize)
        val iendChunk = pngBytes.copyOfRange(pngBytes.size - iendSize, pngBytes.size)

        val totalSize = beforeIend.size + descChunk.size + commentChunk.size + iendChunk.size
        val result = ByteArray(totalSize)

        var pos = 0
        System.arraycopy(beforeIend, 0, result, pos, beforeIend.size)
        pos += beforeIend.size

        System.arraycopy(descChunk, 0, result, pos, descChunk.size)
        pos += descChunk.size

        System.arraycopy(commentChunk, 0, result, pos, commentChunk.size)
        pos += commentChunk.size

        System.arraycopy(iendChunk, 0, result, pos, iendChunk.size)

        return result
    }

    /**
     * Creates a standard PNG tEXt chunk according to the PNG specification.
     */
    private fun createPngTextChunk(keyword: String, text: String): ByteArray {
        val keyBytes = keyword.toByteArray(Charsets.ISO_8859_1)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val dataSize = keyBytes.size + 1 + textBytes.size

        val typeBytes = "tEXt".toByteArray(Charsets.US_ASCII)

        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(keyBytes)
        crc.update(0) // null separator
        crc.update(textBytes)

        val chunk = ByteArray(4 + 4 + dataSize + 4)
        val buffer = ByteBuffer.wrap(chunk).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(dataSize)
        buffer.put(typeBytes)
        buffer.put(keyBytes)
        buffer.put(0.toByte())
        buffer.put(textBytes)
        buffer.putInt(crc.value.toInt())

        return chunk
    }
}
