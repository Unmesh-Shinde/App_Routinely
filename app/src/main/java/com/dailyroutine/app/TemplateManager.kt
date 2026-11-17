package com.dailyroutine.app

import java.util.Locale
import java.time.LocalDate

/** Shared catalog and applied-range behavior for meal and workout templates. */
internal class TemplateManager<T : PlannableTemplate<I>, I : PlannableItem>(
    private val lock: Any,
    private val readTemplates: () -> MutableList<T>,
    private val writeTemplates: (List<T>) -> Unit,
    private val readRanges: () -> MutableList<AppliedTemplateRange>,
    private val writeRanges: (List<AppliedTemplateRange>) -> Unit,
    private val copyWithName: (T, String) -> T
) {
    fun list(): List<T> = synchronized(lock) {
        readTemplates().sortedBy { it.name.lowercase(Locale.US) }
    }

    fun rename(templateId: String, newName: String): Boolean = synchronized(lock) {
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return@synchronized false
        val templates = readTemplates()
        val index = templates.indexOfFirst { it.id == templateId }
        if (index < 0) return@synchronized false
        templates[index] = copyWithName(templates[index], cleanName)
        writeTemplates(templates)
        writeRanges(readRanges().map { range ->
            if (range.templateId == templateId) range.copy(templateName = cleanName) else range
        })
        true
    }

    fun delete(templateId: String) = synchronized(lock) {
        writeTemplates(readTemplates().filterNot { it.id == templateId })
        writeRanges(readRanges().filterNot { it.templateId == templateId })
    }

    fun createFromRange(
        name: String,
        startDate: String,
        endDate: String,
        allowEmpty: Boolean,
        itemsForDate: (String) -> List<I>,
        copyItem: (I) -> I,
        createTemplate: (String, Int, MutableMap<Int, MutableList<I>>) -> T
    ): Boolean = synchronized(lock) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return@synchronized false
        val range = parseDateRange(startDate, endDate) ?: return@synchronized false
        val totalDays = range.second.toEpochDay().minus(range.first.toEpochDay()).toInt() + 1
        val payload = mutableMapOf<Int, MutableList<I>>()
        for (offset in 0 until totalDays) {
            val date = range.first.plusDays(offset.toLong()).toString()
            val items = itemsForDate(date)
            if (items.isNotEmpty()) payload[offset] = items.map(copyItem).toMutableList()
        }
        if (!allowEmpty && payload.isEmpty()) return@synchronized false
        val templates = readTemplates()
        templates.add(createTemplate(cleanName, totalDays, payload))
        writeTemplates(templates)
        true
    }

    fun applyToRange(
        templateId: String,
        startDate: String,
        endDate: String,
        copyItem: (I) -> I,
        saveCopiedItemsByDate: (Map<String, List<I>>) -> Unit,
        validateCopiedItems: (Map<String, List<I>>) -> String? = { null }
    ): TemplateApplyResult = synchronized(lock) {
        val template = readTemplates().firstOrNull { it.id == templateId }
            ?: return@synchronized TemplateApplyResult(applied = false)
        val range = parseDateRange(startDate, endDate)
            ?: return@synchronized TemplateApplyResult(applied = false)
        val (start, end) = range
        val totalDays = end.toEpochDay().minus(start.toEpochDay()).toInt() + 1
        if (totalDays > template.durationDays) {
            return@synchronized TemplateApplyResult(
                applied = false,
                failureReason = "Selected range is $totalDays days, but '${template.name}' is a ${template.durationDays}-day template. Choose a range up to ${template.durationDays} days."
            )
        }
        val existingRanges = readRanges()
        val conflict = existingRanges.firstOrNull { appliedRange ->
            val appliedStart = LocalDate.parse(appliedRange.startDate)
            val appliedEnd = LocalDate.parse(appliedRange.endDate)
            !end.isBefore(appliedStart) && !start.isAfter(appliedEnd)
        }
        if (conflict != null) return@synchronized TemplateApplyResult(applied = false, conflictRange = conflict)

        val copiedItems = mutableMapOf<String, List<I>>()
        for (offset in 0 until totalDays) {
            val templateOffset = offset % template.durationDays
            val date = start.plusDays(offset.toLong()).toString()
            copiedItems[date] = template.itemsByDayOffset[templateOffset].orEmpty().map(copyItem)
        }
        val validationFailure = validateCopiedItems(copiedItems)
        if (!validationFailure.isNullOrBlank()) {
            return@synchronized TemplateApplyResult(applied = false, failureReason = validationFailure)
        }
        saveCopiedItemsByDate(copiedItems)
        writeRanges(existingRanges + AppliedTemplateRange(
            templateId = template.id,
            templateName = template.name,
            startDate = start.toString(),
            endDate = end.toString()
        ))
        TemplateApplyResult(applied = true, appliedEndDate = end.toString())
    }

    private fun parseDateRange(startDate: String, endDate: String): Pair<LocalDate, LocalDate>? = runCatching {
        LocalDate.parse(startDate) to LocalDate.parse(endDate)
    }.getOrNull()?.takeUnless { it.second.isBefore(it.first) }
}
