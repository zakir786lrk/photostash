package com.photostash.app.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max

object VisualCardGenerator {

    private const val CARD_WIDTH = 1080
    private const val PADDING_X = 52f
    private const val PADDING_TOP = 72f

    fun generateCoverBitmap(
        title: String,
        files: List<FileInfo>,
        timestamp: String = FileUtils.getCurrentTimestamp()
    ): Bitmap {
        val totalFiles = files.size
        val totalSizeBytes = files.sumOf { it.size }
        val formattedTotalSize = FileUtils.formatFileSize(totalSizeBytes)

        val useTwoColumns = totalFiles > 8
        val maxGridItems = if (useTwoColumns) 40 else 8
        val displayedGridCount = files.size.coerceAtMost(maxGridItems)

        val rowHeight = if (useTwoColumns) 72f else 88f
        val numRows = if (useTwoColumns) (displayedGridCount + 1) / 2 else displayedGridCount
        val gridHeight = numRows * rowHeight

        // Prepare OCR keywords text containing all file names for Google Photos OCR search
        val ocrKeywords = files.joinToString("   •   ") { it.name }
        val ocrTextPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.parseColor("#E6EDF3")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 23f
        }
        val ocrBoxInnerWidth = (CARD_WIDTH - 2 * PADDING_X - 44f).toInt()
        val ocrLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(ocrKeywords, 0, ocrKeywords.length, ocrTextPaint, ocrBoxInnerWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(6f, 1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(ocrKeywords, ocrTextPaint, ocrBoxInnerWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 6f, false)
        }
        val ocrSectionHeight = 80f + ocrLayout.height.toFloat() + 28f

        // Calculate total card height
        val headerHeight = 310f
        val footerHeight = 90f
        val calculatedHeight = PADDING_TOP + headerHeight + gridHeight + ocrSectionHeight + footerHeight
        val cardHeight = max(1350, calculatedHeight.toInt())

        val bitmap = Bitmap.createBitmap(CARD_WIDTH, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background gradient
        val bgPaint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                0f, 0f, 0f, cardHeight.toFloat(),
                intArrayOf(Color.parseColor("#0D1117"), Color.parseColor("#161B22")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), cardHeight.toFloat(), bgPaint)

        // Outer border accent
        val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#30363D")
        }
        val outerRect = RectF(16f, 16f, CARD_WIDTH - 16f, cardHeight - 16f)
        canvas.drawRoundRect(outerRect, 24f, 24f, borderPaint)

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
            textSize = 26f
        }
        val badgeRect = RectF(PADDING_X, PADDING_TOP, PADDING_X + 320f, PADDING_TOP + 52f)
        canvas.drawRoundRect(badgeRect, 26f, 26f, badgeBgPaint)
        canvas.drawText("🔒 PHOTOSTASH ARCHIVE", PADDING_X + 20f, PADDING_TOP + 36f, badgeTextPaint)

        // 3. Title
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#F0F6FC")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 48f
        }
        val safeTitle = title.ifBlank { "Embedded File Archive" }
        val maxTitleLen = 35
        val truncatedTitle = if (safeTitle.length > maxTitleLen) safeTitle.take(maxTitleLen - 3) + "..." else safeTitle
        canvas.drawText(truncatedTitle, PADDING_X, PADDING_TOP + 120f, titlePaint)

        // 4. Timestamp & Stats
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#8B949E")
            typeface = Typeface.DEFAULT
            textSize = 28f
        }
        canvas.drawText("Created: $timestamp", PADDING_X, PADDING_TOP + 168f, subPaint)

        // Stats Pill Bar: [Count] | [Total Size]
        val statsY = PADDING_TOP + 200f
        val statsBgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#21262D")
        }
        val statsRect = RectF(PADDING_X, statsY, CARD_WIDTH - PADDING_X, statsY + 58f)
        canvas.drawRoundRect(statsRect, 14f, 14f, statsBgPaint)

        val statsTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#E6EDF3")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 26f
        }
        val statsText = "📦 $totalFiles Files   •   💾 $formattedTotalSize Total   •   PNG Polyglot Archive"
        canvas.drawText(statsText, PADDING_X + 24f, statsY + 38f, statsTextPaint)

        // 5. Section Header
        val listStartY = statsY + 95f
        val sectionPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#8B949E")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 24f
            letterSpacing = 0.06f
        }
        canvas.drawText("EMBEDDED CONTENTS (${totalFiles} FILES)", PADDING_X, listStartY, sectionPaint)

        // 6. Draw Grid of Files
        val gridStartY = listStartY + 20f
        val colGap = 20f
        val colWidth = if (useTwoColumns) {
            (CARD_WIDTH - 2 * PADDING_X - colGap) / 2f
        } else {
            CARD_WIDTH - 2 * PADDING_X
        }

        val itemBgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#161B22")
        }
        val itemBorderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.parseColor("#30363D")
        }
        val fileNamePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#F0F6FC")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (useTwoColumns) 24f else 28f
        }
        val fileSizePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#8B949E")
            typeface = Typeface.DEFAULT
            textSize = if (useTwoColumns) 20f else 24f
        }
        val extTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (useTwoColumns) 19f else 22f
        }

        files.take(displayedGridCount).forEachIndexed { index, file ->
            val col = if (useTwoColumns) index % 2 else 0
            val row = if (useTwoColumns) index / 2 else index

            val colLeft = PADDING_X + col * (colWidth + colGap)
            val colRight = colLeft + colWidth
            val rowTop = gridStartY + row * rowHeight
            val rowBottom = rowTop + (rowHeight - 10f)

            val rowRect = RectF(colLeft, rowTop, colRight, rowBottom)
            canvas.drawRoundRect(rowRect, 12f, 12f, itemBgPaint)
            canvas.drawRoundRect(rowRect, 12f, 12f, itemBorderPaint)

            // Extension Pill Badge
            val pillWidth = if (useTwoColumns) 64f else 80f
            val pillHeight = rowBottom - rowTop - 18f
            val pillLeft = colLeft + 10f
            val pillTop = rowTop + 9f
            val pillRect = RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)

            val extColor = getExtensionBadgeColor(file.extension)
            val extBgPaint = Paint().apply {
                isAntiAlias = true
                color = extColor
            }
            canvas.drawRoundRect(pillRect, 8f, 8f, extBgPaint)

            val extLabel = file.extension.take(4)
            val extTextW = extTextPaint.measureText(extLabel)
            canvas.drawText(
                extLabel,
                pillRect.centerX() - (extTextW / 2f),
                pillRect.centerY() + 7f,
                extTextPaint
            )

            // File Name & Size
            val textLeft = pillLeft + pillWidth + 12f
            val maxTextWidth = colWidth - (pillWidth + 28f)

            if (useTwoColumns) {
                // In 2-column mode: Show filename prominently for OCR
                val maxChars = 27
                val display = if (file.name.length > maxChars) {
                    file.name.take(maxChars - 3) + "..."
                } else {
                    file.name
                }
                canvas.drawText(display, textLeft, rowTop + 38f, fileNamePaint)
            } else {
                // In 1-column mode: Show filename and right-aligned size
                val sizeStr = file.formattedSize
                val sizeW = fileSizePaint.measureText(sizeStr)
                val sizeX = colRight - 16f - sizeW
                canvas.drawText(sizeStr, sizeX, rowTop + 48f, fileSizePaint)

                val availForName = sizeX - textLeft - 16f
                val maxChars = 34
                val display = if (file.name.length > maxChars) {
                    file.name.take(maxChars - 3) + "..."
                } else {
                    file.name
                }
                canvas.drawText(display, textLeft, rowTop + 48f, fileNamePaint)
            }
        }

        // 7. OCR Searchable Keywords Card (Every single file name rendered for Google Photos OCR)
        val ocrCardTop = gridStartY + gridHeight + 20f
        val ocrCardRect = RectF(PADDING_X, ocrCardTop, CARD_WIDTH - PADDING_X, ocrCardTop + ocrSectionHeight)
        val ocrCardBgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#13171F")
        }
        val ocrCardBorderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#238636")
        }
        canvas.drawRoundRect(ocrCardRect, 14f, 14f, ocrCardBgPaint)
        canvas.drawRoundRect(ocrCardRect, 14f, 14f, ocrCardBorderPaint)

        val ocrHeaderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#10B981")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 22f
            letterSpacing = 0.05f
        }
        canvas.drawText("🔍 SEARCHABLE FILE INDEX (GOOGLE PHOTOS OCR)", PADDING_X + 20f, ocrCardTop + 38f, ocrHeaderPaint)

        // Draw wrapped file list
        canvas.save()
        canvas.translate(PADDING_X + 22f, ocrCardTop + 56f)
        ocrLayout.draw(canvas)
        canvas.restore()

        // 8. Footer Notice
        val footerY = cardHeight - 48f
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#6E7681")
            typeface = Typeface.DEFAULT
            textSize = 22f
        }
        canvas.drawText(
            "PhotoStash Polyglot • Extract with PhotoStash Android app or run: unzip <image.png>",
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
