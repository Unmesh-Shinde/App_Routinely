package com.dailyroutine.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object WellnessReportManager {

    enum class Parameter(val label: String, val columnLabel: String? = null) {
        STEPS("Steps", "Steps"),
        INTAKE("Calorie intake", "Intake"),
        BURNED("Active calories burned", "Burned"),
        NET("Net calories", "Net"),
        SLEEP("Sleep", "Sleep"),
        WEIGHT("Weight", "Weight"),
        BMI("BMI", "BMI"),
        NOTE("Daily note", "Note"),
        PROFILE_METRICS("Profile metrics"),
        TOTAL_STEPS("Total steps"),
        TOTAL_INTAKE("Total calorie intake"),
        TOTAL_BURNED("Total active calories burned"),
        AVERAGE_SLEEP("Average sleep"),
        AVERAGE_WEIGHT("Average logged weight")
    }

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    suspend fun generateAndShareReport(
        context: Context,
        reportDays: Int,
        selectedParameters: Set<Parameter>
    ) {
        require(reportDays in 1..180)
        require(selectedParameters.isNotEmpty())

        val hdm = HealthDataManager(context)
        val planManager = PlanManager(context)
        val today = Calendar.getInstance()
        val startRange = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(reportDays - 1)) }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val startDate = sdf.format(startRange.time)
        val endDate = sdf.format(today.time)
        val metricsMap = hdm.getMetricsMap(startDate, endDate)
        val reportSelection = ReportParameterSelection(selectedParameters)
        val dailyParameters = reportSelection.dailyParameters.toList()
        val summaryParameters = reportSelection.summaryParameters.toList()

        withContext(Dispatchers.Default) {
            val pdfDocument = PdfDocument()
            val linePaint = Paint().apply { color = Color.LTGRAY }
            val titlePaint = Paint().apply {
                textSize = 21f
                isFakeBoldText = true
                color = Color.rgb(26, 26, 46)
            }
            val headerPaint = Paint().apply {
                textSize = 9.5f
                isFakeBoldText = true
                color = Color.rgb(26, 26, 46)
            }
            val textPaint = Paint().apply {
                textSize = 9f
                color = Color.rgb(45, 45, 60)
            }
            val smallPaint = Paint().apply {
                textSize = 8f
                color = Color.rgb(95, 95, 110)
            }

            val margin = 36f
            val tableRight = PAGE_WIDTH - margin
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas
            var pageNumber = 1
            var y = 50f
            val rowHeight = 17f
            val displaySdf = SimpleDateFormat("dd MMM", Locale.US)

            canvas.drawText("$reportDays-Day Wellness Report - Routinely", margin, y, titlePaint)
            y += 30f
            canvas.drawText("User: ${UserPreferencesStore.getUserName(context)}", margin, y, textPaint)
            y += 18f
            canvas.drawText("Generated on: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())}", margin, y, textPaint)
            y += 18f
            canvas.drawText("Period: $startDate to $endDate ($reportDays days)", margin, y, textPaint)
            y += 24f

            fun drawTableHeader() {
                val columns = listOf("Date") + dailyParameters.map { it.columnLabel!! }
                val columnWidth = (tableRight - margin) / columns.size
                columns.forEachIndexed { index, label ->
                    val x = margin + index * columnWidth
                    val clipped = headerPaint.breakText(label, true, columnWidth - 4f, null)
                    canvas.drawText(label.take(clipped), x, y, headerPaint)
                }
                y += 8f
                canvas.drawLine(margin, y, tableRight, y, linePaint)
                y += 14f
            }

            fun startNewPage() {
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = 45f
                canvas.drawText("$reportDays-Day Wellness Report - continued", margin, y, headerPaint)
                y += 24f
                drawTableHeader()
            }

            canvas.drawText("Daily Details", margin, y, headerPaint)
            y += 16f
            drawTableHeader()

            var totalSteps = 0L
            var totalIntake = 0
            var totalBurned = 0
            var sleepDays = 0
            var sleepTotal = 0.0
            var weightDays = 0
            var weightTotal = 0.0
            val cal = Calendar.getInstance()

            for (dayOffset in 0 until reportDays) {
                if (y > 800) startNewPage()
                val dateStr = sdf.format(cal.time)
                val m = metricsMap[dateStr]
                val steps = m?.steps ?: hdm.getHistoricalSteps(dateStr)
                val sleep = m?.sleepHours ?: hdm.getHistoricalSleep(dateStr)
                val weight = m?.weightKg ?: hdm.getWeight(dateStr)
                val intake = planManager.getMealsForDate(dateStr).sumOf { (it.displayCalories ?: 0).coerceAtLeast(0) }
                val effectiveWeight = if (weight > 0.0) weight else UserPreferencesStore.getUserWeight(context)
                val burned = WellnessEngine.calculateActiveBurnForDateRoom(context, dateStr, steps.toInt(), effectiveWeight, m).toInt()
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
                if (sleep > 0.0) { sleepDays++; sleepTotal += sleep }
                if (weight > 0.0) { weightDays++; weightTotal += weight }

                val values = listOf(displaySdf.format(cal.time)) + dailyParameters.map { parameter ->
                    when (parameter) {
                        Parameter.STEPS -> steps.toString()
                        Parameter.INTAKE -> if (intake > 0) intake.toString() else "-"
                        Parameter.BURNED -> if (burned > 0) burned.toString() else "-"
                        Parameter.NET -> if (intake > 0 || burned > 0) net.toString() else "-"
                        Parameter.SLEEP -> if (sleep > 0.0) "%.1fh".format(sleep) else "-"
                        Parameter.WEIGHT -> if (weight > 0.0) "%.1f".format(weight) else "-"
                        Parameter.BMI -> if (bmi > 0.0) "%.1f".format(bmi) else "-"
                        Parameter.NOTE -> note
                        else -> ""
                    }
                }
                val columnWidth = (tableRight - margin) / values.size
                values.forEachIndexed { index, value ->
                    val x = margin + index * columnWidth
                    val clipped = textPaint.breakText(value, true, columnWidth - 4f, null)
                    canvas.drawText(value.take(clipped), x, y, textPaint)
                }
                y += rowHeight
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }

            if (y > 730) startNewPage()
            y += 14f
            canvas.drawLine(margin, y, tableRight, y, linePaint)
            y += 22f
            if (summaryParameters.isNotEmpty()) {
                canvas.drawText("Summary", margin, y, headerPaint)
                y += 18f
                summaryParameters.forEach { parameter ->
                    val summary = when (parameter) {
                        Parameter.TOTAL_STEPS -> "Total steps: $totalSteps"
                        Parameter.TOTAL_INTAKE -> "Total calorie intake: $totalIntake kcal"
                        Parameter.TOTAL_BURNED -> "Total active calories burned: $totalBurned kcal"
                        Parameter.AVERAGE_SLEEP -> "Average sleep: ${if (sleepDays > 0) "%.1fh".format(sleepTotal / sleepDays) else "-"}"
                        Parameter.AVERAGE_WEIGHT -> "Average logged weight: ${if (weightDays > 0) "%.1f kg".format(weightTotal / weightDays) else "-"}"
                        else -> ""
                    }
                    canvas.drawText(summary, margin, y, textPaint)
                    y += 16f
                    if (y > 800) startNewPage()
                }
            }

            if (reportSelection.includesProfileMetrics) {
                if (y > 770) startNewPage()
                y += if (summaryParameters.isEmpty()) 4f else 8f
                canvas.drawText("Profile metrics", margin, y, headerPaint)
                y += 18f
                val profileMetrics = ProfileHealthMetricsCalculator.calculate(context)
                val description = profileMetrics?.let {
                    "BMI %.1f (%s) • BMR %d kcal/day • Ideal intake %d kcal/day • Ideal weight %.1f kg"
                        .format(it.bmi, it.bmiCategory, it.bmr, it.idealCalories, it.idealWeightKg)
                } ?: "Add profile details to see BMI, BMR, and ideal targets."
                canvas.drawText(description.take(textPaint.breakText(description, true, tableRight - margin, null)), margin, y, textPaint)
            }

            pdfDocument.finishPage(page)
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            val file = File(reportsDir, "routinely_${reportDays}_day_wellness_report_${System.currentTimeMillis()}.pdf")
            try {
                pdfDocument.writeTo(FileOutputStream(file))
                withContext(Dispatchers.Main) { sharePdf(context, file) }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pdfDocument.close()
            }
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
