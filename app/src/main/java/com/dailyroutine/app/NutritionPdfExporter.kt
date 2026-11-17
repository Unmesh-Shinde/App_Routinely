package com.dailyroutine.app

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object NutritionPdfExporter {

    private const val TAG = "NutritionPdfExporter"
    const val PDF_FILE_NAME = "Routinely_Nutrition_Fair_Use_Guide.pdf"

    fun generatePdf(context: Context): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // Standard A4 width in points
            val pageHeight = 842 // Standard A4 height in points
            val margin = 32f

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            var pageFinished = false

            val titlePaint = Paint().apply {
                color = Color.parseColor("#17351E")
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.parseColor("#4CAF50")
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val headerPaint = Paint().apply {
                color = Color.parseColor("#2E7D32")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = Color.parseColor("#333333")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val metaPaint = Paint().apply {
                color = Color.parseColor("#666666")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }

            var y = margin + 18f

            fun finishCurrentPage() {
                if (!pageFinished) {
                    metaPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Page $pageNumber", pageWidth - margin, pageHeight - margin, metaPaint)
                    metaPaint.textAlign = Paint.Align.LEFT
                    pdfDocument.finishPage(page)
                    pageFinished = true
                }
            }

            fun checkPageOverflow(requiredHeight: Float) {
                if (y + requiredHeight > pageHeight - margin - 20f) {
                    finishCurrentPage()

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    pageFinished = false
                    y = margin + 18f
                }
            }

            val isAck = UserSettingsStore.hasAcknowledgedNutritionPolicy(context)
            val ackDate = UserSettingsStore.getNutritionPolicyAcknowledgedDate(context)

            // --- Document Header ---
            canvas.drawText("ROUTINELY - NUTRITION & AI FAIR USE GUIDE", margin, y, titlePaint)
            y += 20f
            canvas.drawText("Official Guidelines for Calorie & Nutrient Estimation Engine", margin, y, subtitlePaint)
            y += 15f

            val headerMeta = if (isAck && !ackDate.isNullOrBlank()) {
                "Document Version: 1.0 • Acknowledgment Record: $ackDate"
            } else {
                "Document Version: 1.0 • Status: Pending Acknowledgment"
            }
            canvas.drawText(headerMeta, margin, y, metaPaint)
            y += 20f

            fun drawSection(title: String, paragraphs: List<String>) {
                checkPageOverflow(24f)
                canvas.drawText(title, margin, y, headerPaint)
                y += 15f

                for (p in paragraphs) {
                    val words = p.split(" ")
                    var line = ""
                    for (w in words) {
                        val testLine = if (line.isEmpty()) w else "$line $w"
                        val isWidthOver = testLine.length > 120
                        if (isWidthOver && line.isNotEmpty()) {
                            checkPageOverflow(13f)
                            canvas.drawText(line, margin, y, bodyPaint)
                            y += 13f
                            line = w
                        } else {
                            line = testLine
                        }
                    }
                    if (line.isNotEmpty()) {
                        checkPageOverflow(13f)
                        canvas.drawText(line, margin, y, bodyPaint)
                        y += 13f
                    }
                    y += 3f
                }
                y += 6f
            }

            drawSection(
                "1. Purpose & Core AI Principles",
                listOf(
                    "Routinely integrates Gemini AI to provide natural language calorie and nutrient estimations for your meal plan.",
                    "To maintain low latency, fast response times, and prevent quota exhaustion for all users, fair-use calculation limits and cached estimation rules are applied."
                )
            )

            drawSection(
                "2. Daily Meal Category Limits",
                listOf(
                    "Each calendar date allows up to two active meals per category (Breakfast, Lunch, Dinner, Snack). This limits saved plan entries; AI request quotas are separate.",
                    "Deleting a meal frees up layout display space on your schedule, allowing replacement meals to be added."
                )
            )

            drawSection(
                "3. AI Estimation Tokens & Delete-Replacement Credits",
                listOf(
                    "Base AI Quota: Each category receives 2 AI calculation tokens per day.",
                    "Delete-Replacement Credit: Deleting a meal grants 1 replacement AI token for that category today.",
                    "Zero-Cost Local Memory: Adding a meal that matches a previously learned meal name uses local offline cache with 0 token cost."
                )
            )

            drawSection(
                "4. Per-Meal Edit Cap & Lifetime Rules",
                listOf(
                    "Updating AI Fields: Changing a meal's Name, Details, or Category consumes 1 allowed edit (capped at 2 lifetime edits per meal).",
                    "Unlimited Non-AI Updates: Adjusting meal time or toggling reminder switches does NOT consume edit attempts."
                )
            )

            drawSection(
                "5. Overnight Off-Peak Batch Queue",
                listOf(
                    "When network delays or temporary server busy states occur after 3 immediate retries, meal calculations are queued for overnight processing.",
                    "Overnight queue items are processed sequentially (1 by 1 in FIFO order) between 12 AM and 6 AM, ensuring results are ready in the morning."
                )
            )

            drawSection(
                "6. Refined 3-Day Discipline Streak System",
                listOf(
                    "Primary Meal Requirement: Days count toward a streak ONLY IF at least 1 primary meal (Breakfast, Lunch, or Dinner) is logged.",
                    "Edit Streak Reward: 3 qualifying days with 0 edits unlocks +1 bonus edit per meal for the next 3 days.",
                    "Delete Streak Reward: 3 qualifying days with 0 deletes unlocks +1 extra delete-replacement credit per category for the next 3 days.",
                    "Automatic Lapse: Streak bonuses remain active for 3 days and automatically lapse on Day 4."
                )
            )

            val ackStatusLine = if (isAck && !ackDate.isNullOrBlank()) {
                "Recorded Acknowledgment Date: $ackDate"
            } else {
                "Recorded Acknowledgment Date: Pending (Not yet acknowledged by user)"
            }

            drawSection(
                "7. User Declaration & Compliance",
                listOf(
                    "By acknowledging this policy, the user agrees to log meals clearly without grammatical errors and adhere to fair-use guidelines.",
                    ackStatusLine
                )
            )

            finishCurrentPage()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val outputFile = File(downloadsDir, PDF_FILE_NAME)
            FileOutputStream(outputFile).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            Log.d(TAG, "Successfully generated PDF: ${outputFile.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write PDF: ${e.message}")
            null
        }
    }
}
