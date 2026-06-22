package com.dailyroutine.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object WellnessReportManager {

    fun generateAndShareReport(context: Context) {
        val hdm = HealthDataManager(context)
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint().apply {
            textSize = 24f
            isFakeBoldText = true
        }
        val headerPaint = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply { textSize = 12f }

        // Start Page
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        var y = 50f
        val margin = 40f
        val colWidth = 75f

        // 1. Title
        canvas.drawText("Steps, Sleep and Calorie Data - Daily Routine App", margin, y, titlePaint)
        y += 30f
        val userName = UserPreferencesStore.getUserName(context)
        canvas.drawText("User: $userName", margin, y, textPaint)
        y += 20f
        val dateRange = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
        canvas.drawText("Generated on: $dateRange", margin, y, textPaint)
        y += 40f

        // 2. Table Headers
        canvas.drawText("Date", margin, y, headerPaint)
        canvas.drawText("Steps", margin + colWidth, y, headerPaint)
        canvas.drawText("Sleep", margin + colWidth * 2, y, headerPaint)
        canvas.drawText("Intake", margin + colWidth * 3, y, headerPaint)
        canvas.drawText("Burned", margin + colWidth * 4, y, headerPaint)
        canvas.drawText("Net", margin + colWidth * 5, y, headerPaint)
        
        y += 10f
        canvas.drawLine(margin, y, 555f, y, paint)
        y += 20f

        // 3. Data Rows (Last 60 Days)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displaySdf = SimpleDateFormat("dd MMM", Locale.US)
        val cal = Calendar.getInstance()

        for (i in 0 until 60) {
            if (y > 800) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 2).create())
                canvas = page.canvas
                y = 50f
            }

            val dateStr = sdf.format(cal.time)
            val displayDate = displaySdf.format(cal.time)
            
            val steps = hdm.getHistoricalSteps(dateStr)
            val sleep = hdm.getHistoricalSleep(dateStr)
            val cals = hdm.getHistoricalCalories(dateStr)
            // Manual check for today in intake
            val intake = 0 // In a full implementation, we'd fetch meal sums from PlanManager per date

            canvas.drawText(displayDate, margin, y, textPaint)
            canvas.drawText(steps.toString(), margin + colWidth, y, textPaint)
            canvas.drawText("%.1fh".format(sleep), margin + colWidth * 2, y, textPaint)
            canvas.drawText("-", margin + colWidth * 3, y, textPaint)
            canvas.drawText("%.0f".format(cals), margin + colWidth * 4, y, textPaint)
            canvas.drawText("-", margin + colWidth * 5, y, textPaint)

            y += 20f
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        pdfDocument.finishPage(page)

        // 4. Save and Share
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        
        val file = File(reportsDir, "wellness_report_${System.currentTimeMillis()}.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            sharePdf(context, file)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
    }

    private fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "Wellness Report")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Wellness Report"))
    }
}
