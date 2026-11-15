package com.photostash.app.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
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

        onProgress(0.1f, "Generating visual index card...")
        val bitmap = coverBitmap ?: VisualCardGenerator.generateCoverBitmap(
            title = title,
            files = files
        )

        // 1. Compress bitmap to PNG byte array
        onProgress(0.3f, "Encoding PNG image...")
        val pngBytes = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }

        // 2. Prepare destination image in MediaStore
        onProgress(0.5f, "Creating output image...")
        val savedUri = FileUtils.saveImageToMediaStore(context, outputFileName) { rawOutputStream ->
            BufferedOutputStream(rawOutputStream).use { bufferedOut ->
                // First: Write PNG binary bytes (including PNG header and IEND chunk)
                bufferedOut.write(pngBytes)
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
}
