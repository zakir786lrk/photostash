package com.photostash.app.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max

object VisualCardGenerator {

    private const val CARD_WIDTH = 1080
    private const val PADDING_X = 64f
    private const val PADDING_TOP = 80f
    private const val ITEM_HEIGHT = 100f
    private const val MAX_VISIBLE_ITEMS = 12

    fun generateCoverBitmap(
        title: String,
        files: List<FileInfo>,
        timestamp: String = FileUtils.getCurrentTimestamp()
    ): Bitmap {
        val totalFiles = files.size
        val totalSizeBytes = files.sumOf { it.size }
        val formattedTotalSize = FileUtils.formatFileSize(totalSizeBytes)

        // Calculate dynamic height based on number of items
        val displayedCount = files.size.coerceAtMost(MAX_VISIBLE_ITEMS)
        val calculatedHeight = PADDING_TOP + 280f + (displayedCount * ITEM_HEIGHT) + 220f
        val cardHeight = max(1350, calculatedHeight.toInt())

        val bitmap = Bitmap.createBitmap(CARD_WIDTH, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background gradient
        val bgPaint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                0f, 0f, 0f, cardHeight.toFloat(),
                intArrayOf(Color.parseColor("#0E1117"), Color.parseColor("#161B22")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), cardHeight.toFloat(), bgPaint)

        // Outer border accent
        val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#21262D")
        }
        val outerRect = RectF(20f, 20f, CARD_WIDTH - 20f, cardHeight - 20f)
        canvas.drawRoundRect(outerRect, 28f, 28f, borderPaint)

        // 2. Header Tag Badge: "PHOTOSTASH ARCHIVE"
        val badgeBgPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.parseColor("#10B981")
        }
        val badgeTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#0E1117")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 28f
        }
        val badgeRect = RectF(PADDING_X, PADDING_TOP, PADDING_X + 340f, PADDING_TOP + 56f)
        canvas.drawRoundRect(badgeRect, 28f, 28f, badgeBgPaint)
        canvas.drawText("🔒 PHOTOSTASH ARCHIVE", PADDING_X + 24f, PADDING_TOP + 38f, badgeTextPaint)

        // 3. Title
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#F0F6FC")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 52f
        }
        val safeTitle = title.ifBlank { "Embedded File Archive" }
        val truncatedTitle = if (safeTitle.length > 28) safeTitle.take(25) + "..." else safeTitle
        canvas.drawText(truncatedTitle, PADDING_X, PADDING_TOP + 130f, titlePaint)

        // 4. Timestamp & Stats
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#8B949E")
            typeface = Typeface.DEFAULT
            textSize = 30f
        }
        canvas.drawText("Created: $timestamp", PADDING_X, PADDING_TOP + 180f, subPaint)

        // Stats Pill Bar: [Count] | [Total Size]
        val statsY = PADDING_TOP + 215f
        val statsBgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#21262D")
        }
        val statsRect = RectF(PADDING_X, statsY, CARD_WIDTH - PADDING_X, statsY + 64f)
        canvas.drawRoundRect(statsRect, 16f, 16f, statsBgPaint)

        val statsTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#E6EDF3")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 28f
        }
        val statsText = "📦 $totalFiles Files  •  💾 $formattedTotalSize Total  •  PNG Polyglot"
        canvas.drawText(statsText, PADDING_X + 28f, statsY + 42f, statsTextPaint)

        // 5. Files List Header
        val listStartY = statsY + 110f
        val sectionPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#8B949E")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 26f
        }
        canvas.drawText("EMBEDDED CONTENTS", PADDING_X, listStartY, sectionPaint)

        // 6. File Rows
        var currentY = listStartY + 30f
        val itemBgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#161B22")
        }
        val itemBorderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#30363D")
        }
        val fileNamePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#F0F6FC")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 32f
        }
        val fileSizePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#8B949E")
            typeface = Typeface.DEFAULT
            textSize = 28f
        }

        files.take(MAX_VISIBLE_ITEMS).forEach { file ->
            val rowRect = RectF(PADDING_X, currentY, CARD_WIDTH - PADDING_X, currentY + 84f)
            canvas.drawRoundRect(rowRect, 16f, 16f, itemBgPaint)
            canvas.drawRoundRect(rowRect, 16f, 16f, itemBorderPaint)

            // Extension pill badge
            val extColor = getExtensionBadgeColor(file.extension)
            val extBgPaint = Paint().apply {
                isAntiAlias = true
                color = extColor
            }
            val extTextPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 22f
            }
            val extPill = RectF(PADDING_X + 16f, currentY + 16f, PADDING_X + 116f, currentY + 68f)
            canvas.drawRoundRect(extPill, 12f, 12f, extBgPaint)

            val extText = file.extension.take(4)
            val extWidth = extTextPaint.measureText(extText)
            canvas.drawText(
                extText,
                extPill.centerX() - (extWidth / 2f),
                extPill.centerY() + 8f,
                extTextPaint
            )

            // File Name
            val maxNameLen = 32
            val nameDisplay = if (file.name.length > maxNameLen) {
                file.name.take(maxNameLen - 3) + "..."
            } else {
                file.name
            }
            canvas.drawText(nameDisplay, PADDING_X + 136f, currentY + 54f, fileNamePaint)

            // File Size (Right aligned)
            val sizeStr = file.formattedSize
            val sizeWidth = fileSizePaint.measureText(sizeStr)
            canvas.drawText(sizeStr, CARD_WIDTH - PADDING_X - 24f - sizeWidth, currentY + 54f, fileSizePaint)

            currentY += ITEM_HEIGHT
        }

        // If truncated
        if (files.size > MAX_VISIBLE_ITEMS) {
            val moreCount = files.size - MAX_VISIBLE_ITEMS
            val morePaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#58A6FF")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 28f
            }
            canvas.drawText("+ $moreCount more files included inside", PADDING_X + 16f, currentY + 36f, morePaint)
            currentY += 50f
        }

        // 7. Footer Notice
        val footerY = cardHeight - 70f
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#6E7681")
            typeface = Typeface.DEFAULT
            textSize = 24f
        }
        canvas.drawText(
            "Extract with PhotoStash Android App or run: unzip <image.png>",
            PADDING_X,
            footerY,
            footerPaint
        )

        return bitmap
    }

    private fun getExtensionBadgeColor(ext: String): Int {
        return when (ext.uppercase()) {
            "PDF" -> Color.parseColor("#EF4444")
            "DOC", "DOCX" -> Color.parseColor("#3B82F6")
            "XLS", "XLSX", "CSV" -> Color.parseColor("#10B981")
            "ZIP", "RAR", "7Z", "TAR", "GZ" -> Color.parseColor("#8B5CF6")
            "JPG", "JPEG", "PNG", "WEBP", "GIF" -> Color.parseColor("#F59E0B")
            "MP3", "WAV", "OGG", "M4A" -> Color.parseColor("#EC4899")
            "MP4", "MKV", "MOV" -> Color.parseColor("#14B8A6")
            "TXT", "MD", "JSON", "XML", "HTML", "PY", "KT" -> Color.parseColor("#06B6D4")
            else -> Color.parseColor("#4B5563")
        }
    }
}
