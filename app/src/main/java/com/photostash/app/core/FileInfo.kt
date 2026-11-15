package com.photostash.app.core

import android.net.Uri

data class FileInfo(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String
) {
    val formattedSize: String
        get() = FileUtils.formatFileSize(size)

    val extension: String
        get() = name.substringAfterLast('.', "").uppercase().ifEmpty { "BIN" }
}

data class EmbeddedEntry(
    val name: String,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val timeMillis: Long
) {
    val formattedSize: String
        get() = FileUtils.formatFileSize(uncompressedSize)

    val extension: String
        get() = name.substringAfterLast('.', "").uppercase().ifEmpty { "BIN" }
}
