package com.photostash.app.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun resolveFileInfo(context: Context, uri: Uri): FileInfo {
        var name = "unknown_file"
        var size = 0L
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: name
                }
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        // Fallback for file:// or unresolvable size
        if (size <= 0L) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    size = stream.available().toLong()
                }
            } catch (_: Exception) {}
        }

        return FileInfo(uri = uri, name = name, size = size, mimeType = mimeType)
    }

    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
            .coerceIn(0, units.size - 1)
        val df = DecimalFormat("#,##0.#")
        return "${df.format(sizeBytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }

    fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    fun saveImageToMediaStore(
        context: Context,
        displayName: String,
        title: String? = null,
        description: String? = null,
        writeBlock: (OutputStream) -> Unit
    ): Uri? {
        val filename = if (displayName.endsWith(".png", ignoreCase = true)) {
            displayName
        } else {
            "$displayName.png"
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (!title.isNullOrBlank()) {
                put(MediaStore.Images.Media.TITLE, title)
            }
            if (!description.isNullOrBlank()) {
                put(MediaStore.Images.Media.DESCRIPTION, description)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoStash")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            } else {
                MediaStore.VOLUME_EXTERNAL
            }
        )

        val imageUri = resolver.insert(collection, values) ?: return null

        try {
            resolver.openOutputStream(imageUri)?.use { outputStream ->
                writeBlock(outputStream)
            }

            if (!description.isNullOrBlank()) {
                try {
                    resolver.openFileDescriptor(imageUri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, description)
                        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, description)
                        exif.saveAttributes()
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, values, null, null)
            }
            return imageUri
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                resolver.delete(imageUri, null, null)
            } catch (_: Exception) {}
            return null
        }
    }

    fun saveExtractedFileToDownloads(
        context: Context,
        subFolder: String,
        fileName: String,
        writeBlock: (OutputStream) -> Unit
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PhotoStash/$subFolder")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val targetUri = resolver.insert(collection, values) ?: return null

        try {
            resolver.openOutputStream(targetUri)?.use { stream ->
                writeBlock(stream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(targetUri, values, null, null)
            }
            return targetUri
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                resolver.delete(targetUri, null, null)
            } catch (_: Exception) {}
            return null
        }
    }
}
