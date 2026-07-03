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

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val REPORT_DAYS = 180

    fun generateAndShareReport(context: Context) {
        val hdm = HealthDataManager(context)
        val planManager = PlanManager(context)
        val pdfDocument = PdfDocument()
        val linePaint = Paint().apply { color = Color.LTGRAY }
        val titlePaint = Paint().apply {
            textSize = 21f
            isFakeBoldText = true
            color = Color.rgb(26, 26, 46)
        }
        val headerPaint = Paint().apply {
            textSize = 10.5f
            isFakeBoldText = true
            color = Color.rgb(26, 26, 46)
        }
        val textPaint = Paint().apply {
            textSize = 9.5f
            color = Color.rgb(45, 45, 60)
        }
        val smallPaint = Paint().apply {
            textSize = 8.5f
            color = Color.rgb(95, 95, 110)
        }

        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var pageNumber = 1

        var y = 50f
        val margin = 40f
        val rowHeight = 17f
        val colDate = margin
        val colSteps = 105f
        val colIntake = 165f
        val colBurned = 230f
        val colNet = 295f
        val colSleep = 350f
        val colWeight = 405f
        val colBmi = 462f
        val colNote = 505f

        canvas.drawText("180-Day Wellness Report - Routinely", margin, y, titlePaint)
        y += 30f
        val userName = UserPreferencesStore.getUserName(context)
        canvas.drawText("User: $userName", margin, y, textPaint)
        y += 20f
        val generatedOn = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())
        canvas.drawText("Generated on: $generatedOn", margin, y, textPaint)
        y += 18f
        canvas.drawText("Scope: Steps, Calories, Sleep, and Weight for the past $REPORT_DAYS days", margin, y, textPaint)
        y += 24f

        ProfileHealthMetricsCalculator.calculate(context)?.let { metrics ->
            canvas.drawText(
                "Profile metrics: BMI %.1f (${metrics.bmiCategory}) • BMR ${metrics.bmr} kcal/day • Ideal intake ${metrics.idealCalories} kcal/day • Ideal weight %.1f kg"
                    .format(metrics.bmi, metrics.idealWeightKg),
                margin,
                y,
                textPaint
            )
            y += 22f
        }

        canvas.drawText("Daily Details", margin, y, headerPaint)
        y += 16f

        fun drawTableHeader() {
            canvas.drawText("Date", colDate, y, headerPaint)
            canvas.drawText("Steps", colSteps, y, headerPaint)
            canvas.drawText("Intake", colIntake, y, headerPaint)
            canvas.drawText("Burned", colBurned, y, headerPaint)
            canvas.drawText("Net", colNet, y, headerPaint)
            canvas.drawText("Sleep", colSleep, y, headerPaint)
            canvas.drawText("Weight", colWeight, y, headerPaint)
            canvas.drawText("BMI", colBmi, y, headerPaint)
            canvas.drawText("Note", colNote, y, headerPaint)
            y += 8f
            canvas.drawLine(margin, y, 555f, y, linePaint)
            y += 14f
        }

        fun startNewPage() {
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 45f
            canvas.drawText("180-Day Wellness Report - continued", margin, y, headerPaint)
            y += 24f
            drawTableHeader()
        }

        drawTableHeader()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displaySdf = SimpleDateFormat("dd MMM", Locale.US)
        val cal = Calendar.getInstance()
        var totalSteps = 0L
        var totalIntake = 0
        var totalBurned = 0
        var sleepDays = 0
        var sleepTotal = 0.0
        var weightDays = 0
        var weightTotal = 0.0

        for (i in 0 until REPORT_DAYS) {
            if (y > 800) startNewPage()

            val dateStr = sdf.format(cal.time)
            val displayDate = displaySdf.format(cal.time)
            val steps = hdm.getHistoricalSteps(dateStr)
            val sleep = hdm.getHistoricalSleep(dateStr)
            val weight = hdm.getWeight(dateStr)
            val intake = planManager.getMealsForDate(dateStr).sumOf { it.calories.coerceAtLeast(0) }
            val effectiveWeight = if (weight > 0.0) weight else UserPreferencesStore.getUserWeight(context)
            val burned = WellnessEngine.calculateActiveBurnForDate(context, dateStr, steps.toInt(), effectiveWeight).toInt()
            val net = intake - burned
            val bmi = ProfileHealthMetricsCalculator.calculate(
                age = UserPreferencesStore.getUserAge(context),
                heightCm = UserPreferencesStore.getUserHeight(context),
                weightKg = effectiveWeight,
                gender = UserPreferencesStore.getUserGender(context)
            )?.bmi ?: 0.0
            val note = when {
                intake == 0 && steps == 0L && sleep == 0.0 && weight == 0.0 -> "No data"
                net > (ProfileHealthMetricsCalculator.calculate(context)?.idealCalories ?: Int.MAX_VALUE) -> "High net"
                else -> ""
            }

            totalSteps += steps
            totalIntake += intake
            totalBurned += burned
            if (sleep > 0.0) {
                sleepDays++
                sleepTotal += sleep
            }
            if (weight > 0.0) {
                weightDays++
                weightTotal += weight
            }

            canvas.drawText(displayDate, margin, y, textPaint)
            canvas.drawText(steps.toString(), colSteps, y, textPaint)
            canvas.drawText(if (intake > 0) intake.toString() else "-", colIntake, y, textPaint)
            canvas.drawText(if (burned > 0) burned.toString() else "-", colBurned, y, textPaint)
            canvas.drawText(if (intake > 0 || burned > 0) net.toString() else "-", colNet, y, textPaint)
            canvas.drawText(if (sleep > 0.0) "%.1fh".format(sleep) else "-", colSleep, y, textPaint)
            canvas.drawText(if (weight > 0.0) "%.1f".format(weight) else "-", colWeight, y, textPaint)
            canvas.drawText(if (bmi > 0.0) "%.1f".format(bmi) else "-", colBmi, y, textPaint)
            canvas.drawText(note, colNote, y, smallPaint)

            y += rowHeight
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        if (y > 720) startNewPage()
        y += 14f
        canvas.drawLine(margin, y, 555f, y, linePaint)
        y += 22f
        canvas.drawText("Summary", margin, y, headerPaint)
        y += 18f
        canvas.drawText("Total steps: $totalSteps", margin, y, textPaint)
        canvas.drawText("Total intake: $totalIntake kcal", margin + 180f, y, textPaint)
        canvas.drawText("Total active burn: $totalBurned kcal", margin + 340f, y, textPaint)
        y += 18f
        canvas.drawText("Average sleep: ${if (sleepDays > 0) "%.1fh".format(sleepTotal / sleepDays) else "-"}", margin, y, textPaint)
        canvas.drawText("Average logged weight: ${if (weightDays > 0) "%.1f kg".format(weightTotal / weightDays) else "-"}", margin + 180f, y, textPaint)

        pdfDocument.finishPage(page)

        // 4. Save and Share
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        
        val file = File(reportsDir, "routinely_180_day_wellness_report_${System.currentTimeMillis()}.pdf")
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
