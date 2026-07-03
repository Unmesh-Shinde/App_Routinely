package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class TemplateApplyResult(
    val applied: Boolean,
    val conflictRange: AppliedTemplateRange? = null,
    val appliedEndDate: String? = null,
    val failureReason: String? = null
)

class PlanManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("plans_pref", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private companion object {
        private const val KEY_DIET_CALENDAR = "diet_calendar_data"
        private const val KEY_WORKOUT_CALENDAR = "workout_calendar_data"
        private const val KEY_MEAL_TEMPLATES = "meal_templates"
        private const val KEY_WORKOUT_TEMPLATES = "workout_templates"
        private const val KEY_APPLIED_MEAL_TEMPLATE_RANGES = "applied_meal_template_ranges"
        private const val KEY_APPLIED_WORKOUT_TEMPLATE_RANGES = "applied_workout_template_ranges"
    }

    // --- Calendar-Based Diet Plan Management ---

    private fun getFullDietPlan(): DietPlan {
        val json = prefs.getString(KEY_DIET_CALENDAR, null)
        return if (json != null) {
            val type = object : TypeToken<DietPlan>() {}.type
            gson.fromJson(json, type) ?: DietPlan()
        } else {
            DietPlan()
        }
    }

    fun getMealsForDate(date: String): MutableList<Meal> {
        return getFullDietPlan().dailyMeals[date] ?: mutableListOf()
    }

    fun saveMealForDate(date: String, meal: Meal) {
        val plan = getFullDietPlan()
        val list = plan.dailyMeals.getOrPut(date) { mutableListOf() }
        val idx = list.indexOfFirst { it.id == meal.id }
        if (idx >= 0) list[idx] = meal else list.add(meal)

        prefs.edit().putString(KEY_DIET_CALENDAR, gson.toJson(plan)).apply()
    }

    fun deleteMealForDate(date: String, meal: Meal) {
        val plan = getFullDietPlan()
        plan.dailyMeals[date]?.removeIf { it.id == meal.id }
        if (plan.dailyMeals[date].isNullOrEmpty()) {
            plan.dailyMeals.remove(date)
        }
        prefs.edit().putString(KEY_DIET_CALENDAR, gson.toJson(plan)).apply()
    }

    fun hasMealsForDate(date: String): Boolean {
        return !getFullDietPlan().dailyMeals[date].isNullOrEmpty()
    }

    private fun getMealTemplates(): MutableList<MealTemplate> {
        val json = prefs.getString(KEY_MEAL_TEMPLATES, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<MealTemplate>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveMealTemplates(templates: List<MealTemplate>) {
        prefs.edit().putString(KEY_MEAL_TEMPLATES, gson.toJson(templates)).apply()
    }

    fun listMealTemplates(): List<MealTemplate> = getMealTemplates().sortedBy { it.name.lowercase(Locale.US) }

    fun createMealTemplate(name: String, startDate: String, durationDays: Int): Boolean {
        val start = parseDate(startDate)
        val end = addDays(start, durationDays - 1)
        return createMealTemplateFromRange(name, formatDate(start), formatDate(end), allowEmpty = false)
    }

    fun createMealTemplateFromRange(name: String, startDate: String, endDate: String, allowEmpty: Boolean): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return false

        val start = parseDate(startDate)
        val end = parseDate(endDate)
        if (end.before(start)) return false

        val totalDays = daysBetweenInclusive(start, end)
        val templates = getMealTemplates()
        val payload = mutableMapOf<Int, MutableList<Meal>>()

        for (offset in 0 until totalDays) {
            val date = formatDate(addDays(start, offset))
            val meals = getMealsForDate(date)
            if (meals.isNotEmpty()) {
                payload[offset] = meals.map { it.copy() }.toMutableList()
            }
        }

        if (!allowEmpty && payload.isEmpty()) return false

        templates.add(
            MealTemplate(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                durationDays = totalDays,
                mealsByDayOffset = payload
            )
        )
        saveMealTemplates(templates)
        return true
    }

    fun renameMealTemplate(templateId: String, newName: String): Boolean {
        val templates = getMealTemplates()
        val idx = templates.indexOfFirst { it.id == templateId }
        if (idx < 0) return false
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return false
        templates[idx] = templates[idx].copy(name = cleanName)
        saveMealTemplates(templates)
        val ranges = getAppliedMealTemplateRanges().map { range ->
            if (range.templateId == templateId) range.copy(templateName = cleanName) else range
        }
        saveAppliedMealTemplateRanges(ranges)
        return true
    }

    fun duplicateMealTemplate(templateId: String, newName: String): Boolean {
        val original = getMealTemplates().firstOrNull { it.id == templateId } ?: return false
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return false
        val templates = getMealTemplates()
        val copiedMeals = original.mealsByDayOffset.mapValues { entry ->
            entry.value.map { it.copy() }.toMutableList()
        }.toMutableMap()
        templates.add(
            original.copy(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                mealsByDayOffset = copiedMeals
            )
        )
        saveMealTemplates(templates)
        return true
    }

    fun deleteMealTemplate(templateId: String) {
        val templates = getMealTemplates().toMutableList()
        templates.removeAll { it.id == templateId }
        saveMealTemplates(templates)
        val ranges = getAppliedMealTemplateRanges().toMutableList()
        ranges.removeAll { it.templateId == templateId }
        saveAppliedMealTemplateRanges(ranges)
    }

    private fun getAppliedMealTemplateRanges(): MutableList<AppliedTemplateRange> {
        val json = prefs.getString(KEY_APPLIED_MEAL_TEMPLATE_RANGES, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<AppliedTemplateRange>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveAppliedMealTemplateRanges(ranges: List<AppliedTemplateRange>) {
        prefs.edit().putString(KEY_APPLIED_MEAL_TEMPLATE_RANGES, gson.toJson(ranges)).apply()
    }

    fun listAppliedMealTemplateRanges(): List<AppliedTemplateRange> {
        return getAppliedMealTemplateRanges().sortedBy { it.startDate }
    }

    fun findAppliedMealRangesInRange(startDate: String, endDate: String): List<AppliedTemplateRange> {
        val start = parseDate(startDate)
        val end = parseDate(endDate)
        return getAppliedMealTemplateRanges().filter { range ->
            val rangeStart = parseDate(range.startDate)
            val rangeEnd = parseDate(range.endDate)
            !end.before(rangeStart) && !start.after(rangeEnd)
        }.sortedBy { it.startDate }
    }

    fun removeAppliedMealTemplateRange(startDate: String, endDate: String): Boolean {
        val ranges = getAppliedMealTemplateRanges().toMutableList()
        val removed = ranges.removeAll { it.startDate == startDate && it.endDate == endDate }
        if (removed) {
            saveAppliedMealTemplateRanges(ranges)
        }
        return removed
    }

    fun isDateInAppliedMealTemplateRange(date: String): Boolean {
        val day = parseDate(date)
        return getAppliedMealTemplateRanges().any { range ->
            val start = parseDate(range.startDate)
            val end = parseDate(range.endDate)
            !day.before(start) && !day.after(end)
        }
    }

    fun applyMealTemplate(templateId: String, startDate: String): TemplateApplyResult {
        val template = getMealTemplates().firstOrNull { it.id == templateId }
            ?: return TemplateApplyResult(applied = false)
        val start = parseDate(startDate)
        val end = addDays(start, template.durationDays - 1)
        return applyMealTemplateToRange(templateId, formatDate(start), formatDate(end))
    }

    fun applyMealTemplateToRange(templateId: String, startDate: String, endDate: String): TemplateApplyResult {
        val template = getMealTemplates().firstOrNull { it.id == templateId }
            ?: return TemplateApplyResult(applied = false)

        val start = parseDate(startDate)
        val end = parseDate(endDate)
        if (end.before(start)) return TemplateApplyResult(applied = false)

        val totalDays = daysBetweenInclusive(start, end)
        if (totalDays > template.durationDays) {
            return TemplateApplyResult(
                applied = false,
                failureReason = "Selected range is $totalDays days, but '${template.name}' is a ${template.durationDays}-day template. Choose a range up to ${template.durationDays} days."
            )
        }

        val conflict = findOverlap(getAppliedMealTemplateRanges(), start, end)
        if (conflict != null) {
            return TemplateApplyResult(applied = false, conflictRange = conflict)
        }

        for (offset in 0 until totalDays) {
            val templateOffset = offset % template.durationDays
            val meals = template.mealsByDayOffset[templateOffset] ?: emptyList()
            val targetDate = formatDate(addDays(start, offset))
            meals.forEach { original ->
                val cloned = original.copy(id = generateItemId())
                saveMealForDate(targetDate, cloned)
            }
        }

        val ranges = getAppliedMealTemplateRanges().toMutableList()
        ranges.add(
            AppliedTemplateRange(
                templateId = template.id,
                templateName = template.name,
                startDate = formatDate(start),
                endDate = formatDate(end)
            )
        )
        saveAppliedMealTemplateRanges(ranges)
        return TemplateApplyResult(applied = true, appliedEndDate = formatDate(end))
    }

    // --- Calendar-Based Workout Plan Management ---

    private fun getFullWorkoutPlan(): WorkoutPlan {
        val json = prefs.getString(KEY_WORKOUT_CALENDAR, null)
        return if (json != null) {
            val type = object : TypeToken<WorkoutPlan>() {}.type
            gson.fromJson(json, type) ?: WorkoutPlan()
        } else {
            WorkoutPlan()
        }
    }

    fun getExercisesForDate(date: String): MutableList<Exercise> {
        return getFullWorkoutPlan().dailyExercises[date] ?: mutableListOf()
    }

    fun saveExerciseForDate(date: String, ex: Exercise) {
        val plan = getFullWorkoutPlan()
        val list = plan.dailyExercises.getOrPut(date) { mutableListOf() }
        val idx = list.indexOfFirst { it.id == ex.id }
        if (idx >= 0) list[idx] = ex else list.add(ex)
        prefs.edit().putString(KEY_WORKOUT_CALENDAR, gson.toJson(plan)).apply()
    }

    fun deleteExerciseForDate(date: String, ex: Exercise) {
        val plan = getFullWorkoutPlan()
        plan.dailyExercises[date]?.removeIf { it.id == ex.id }
        if (plan.dailyExercises[date].isNullOrEmpty()) {
            plan.dailyExercises.remove(date)
        }
        prefs.edit().putString(KEY_WORKOUT_CALENDAR, gson.toJson(plan)).apply()
    }

    fun hasExercisesForDate(date: String): Boolean {
        return !getFullWorkoutPlan().dailyExercises[date].isNullOrEmpty()
    }

    private fun getWorkoutTemplates(): MutableList<WorkoutTemplate> {
        val json = prefs.getString(KEY_WORKOUT_TEMPLATES, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<WorkoutTemplate>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveWorkoutTemplates(templates: List<WorkoutTemplate>) {
        prefs.edit().putString(KEY_WORKOUT_TEMPLATES, gson.toJson(templates)).apply()
    }

    fun listWorkoutTemplates(): List<WorkoutTemplate> = getWorkoutTemplates().sortedBy { it.name.lowercase(Locale.US) }

    fun createWorkoutTemplate(name: String, startDate: String, durationDays: Int): Boolean {
        val start = parseDate(startDate)
        val end = addDays(start, durationDays - 1)
        return createWorkoutTemplateFromRange(name, formatDate(start), formatDate(end), allowEmpty = false)
    }

    fun createWorkoutTemplateFromRange(name: String, startDate: String, endDate: String, allowEmpty: Boolean): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return false

        val start = parseDate(startDate)
        val end = parseDate(endDate)
        if (end.before(start)) return false

        val totalDays = daysBetweenInclusive(start, end)
        val templates = getWorkoutTemplates()
        val payload = mutableMapOf<Int, MutableList<Exercise>>()

        for (offset in 0 until totalDays) {
            val date = formatDate(addDays(start, offset))
            val exercises = getExercisesForDate(date)
            if (exercises.isNotEmpty()) {
                payload[offset] = exercises.map { it.copy() }.toMutableList()
            }
        }

        if (!allowEmpty && payload.isEmpty()) return false

        templates.add(
            WorkoutTemplate(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                durationDays = totalDays,
                exercisesByDayOffset = payload
            )
        )
        saveWorkoutTemplates(templates)
        return true
    }

    fun renameWorkoutTemplate(templateId: String, newName: String): Boolean {
        val templates = getWorkoutTemplates()
        val idx = templates.indexOfFirst { it.id == templateId }
        if (idx < 0) return false
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return false
        templates[idx] = templates[idx].copy(name = cleanName)
        saveWorkoutTemplates(templates)
        val ranges = getAppliedWorkoutTemplateRanges().map { range ->
            if (range.templateId == templateId) range.copy(templateName = cleanName) else range
        }
        saveAppliedWorkoutTemplateRanges(ranges)
        return true
    }

    fun duplicateWorkoutTemplate(templateId: String, newName: String): Boolean {
        val original = getWorkoutTemplates().firstOrNull { it.id == templateId } ?: return false
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return false
        val templates = getWorkoutTemplates()
        val copiedExercises = original.exercisesByDayOffset.mapValues { entry ->
            entry.value.map { it.copy() }.toMutableList()
        }.toMutableMap()
        templates.add(
            original.copy(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                exercisesByDayOffset = copiedExercises
            )
        )
        saveWorkoutTemplates(templates)
        return true
    }

    fun deleteWorkoutTemplate(templateId: String) {
        val templates = getWorkoutTemplates().toMutableList()
        templates.removeAll { it.id == templateId }
        saveWorkoutTemplates(templates)
        val ranges = getAppliedWorkoutTemplateRanges().toMutableList()
        ranges.removeAll { it.templateId == templateId }
        saveAppliedWorkoutTemplateRanges(ranges)
    }

    private fun getAppliedWorkoutTemplateRanges(): MutableList<AppliedTemplateRange> {
        val json = prefs.getString(KEY_APPLIED_WORKOUT_TEMPLATE_RANGES, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<AppliedTemplateRange>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveAppliedWorkoutTemplateRanges(ranges: List<AppliedTemplateRange>) {
        prefs.edit().putString(KEY_APPLIED_WORKOUT_TEMPLATE_RANGES, gson.toJson(ranges)).apply()
    }

    fun listAppliedWorkoutTemplateRanges(): List<AppliedTemplateRange> {
        return getAppliedWorkoutTemplateRanges().sortedBy { it.startDate }
    }

    fun findAppliedWorkoutRangesInRange(startDate: String, endDate: String): List<AppliedTemplateRange> {
        val start = parseDate(startDate)
        val end = parseDate(endDate)
        return getAppliedWorkoutTemplateRanges().filter { range ->
            val rangeStart = parseDate(range.startDate)
            val rangeEnd = parseDate(range.endDate)
            !end.before(rangeStart) && !start.after(rangeEnd)
        }.sortedBy { it.startDate }
    }

    fun removeAppliedWorkoutTemplateRange(startDate: String, endDate: String): Boolean {
        val ranges = getAppliedWorkoutTemplateRanges().toMutableList()
        val removed = ranges.removeAll { it.startDate == startDate && it.endDate == endDate }
        if (removed) {
            saveAppliedWorkoutTemplateRanges(ranges)
        }
        return removed
    }

    fun isDateInAppliedWorkoutTemplateRange(date: String): Boolean {
        val day = parseDate(date)
        return getAppliedWorkoutTemplateRanges().any { range ->
            val start = parseDate(range.startDate)
            val end = parseDate(range.endDate)
            !day.before(start) && !day.after(end)
        }
    }

    fun applyWorkoutTemplate(templateId: String, startDate: String): TemplateApplyResult {
        val template = getWorkoutTemplates().firstOrNull { it.id == templateId }
            ?: return TemplateApplyResult(applied = false)
        val start = parseDate(startDate)
        val end = addDays(start, template.durationDays - 1)
        return applyWorkoutTemplateToRange(templateId, formatDate(start), formatDate(end))
    }

    fun applyWorkoutTemplateToRange(templateId: String, startDate: String, endDate: String): TemplateApplyResult {
        val template = getWorkoutTemplates().firstOrNull { it.id == templateId }
            ?: return TemplateApplyResult(applied = false)

        val start = parseDate(startDate)
        val end = parseDate(endDate)
        if (end.before(start)) return TemplateApplyResult(applied = false)

        val totalDays = daysBetweenInclusive(start, end)
        if (totalDays > template.durationDays) {
            return TemplateApplyResult(
                applied = false,
                failureReason = "Selected range is $totalDays days, but '${template.name}' is a ${template.durationDays}-day template. Choose a range up to ${template.durationDays} days."
            )
        }

        val conflict = findOverlap(getAppliedWorkoutTemplateRanges(), start, end)
        if (conflict != null) {
            return TemplateApplyResult(applied = false, conflictRange = conflict)
        }

        for (offset in 0 until totalDays) {
            val templateOffset = offset % template.durationDays
            val exercises = template.exercisesByDayOffset[templateOffset] ?: emptyList()
            val targetDate = formatDate(addDays(start, offset))
            exercises.forEach { original ->
                val cloned = original.copy(id = generateItemId())
                saveExerciseForDate(targetDate, cloned)
            }
        }

        val ranges = getAppliedWorkoutTemplateRanges().toMutableList()
        ranges.add(
            AppliedTemplateRange(
                templateId = template.id,
                templateName = template.name,
                startDate = formatDate(start),
                endDate = formatDate(end)
            )
        )
        saveAppliedWorkoutTemplateRanges(ranges)
        return TemplateApplyResult(applied = true, appliedEndDate = formatDate(end))
    }

    fun addQuickIndianDiet(date: String) {
        saveMealForDate(date, Meal(name = "Oats & Milk", mealType = "Breakfast", hour = 8, minute = 30))
        saveMealForDate(date, Meal(name = "Roti & Sabzi", mealType = "Lunch", hour = 13, minute = 0))
        saveMealForDate(date, Meal(name = "Dal & Rice", mealType = "Dinner", hour = 20, minute = 30))
    }

    // Updated sync logic for date-based exercises
    fun syncExerciseReminder(context: Context, ex: Exercise, date: String) {
        val mgr = ReminderManager(context)
        if (!ex.isReminderEnabled) {
            mgr.deleteReminder(Reminder(id = ex.id))
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calDate = sdf.parse(date) ?: Date()
        val calendar = Calendar.getInstance().apply { time = calDate }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        mgr.saveReminder(Reminder(
            id = ex.id,
            title = "Exercise: ${ex.name}",
            type = ReminderType.EXERCISE,
            hour = ex.hour,
            minute = ex.minute,
            repeatDays = listOf(dayOfWeek),
            isHidden = true
        ))
    }

    fun syncMealReminder(context: Context, meal: Meal, date: String) {
        val mgr = ReminderManager(context)
        if (!meal.isReminderEnabled) {
            mgr.deleteReminder(Reminder(id = meal.id))
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calDate = sdf.parse(date) ?: Date()
        val calendar = Calendar.getInstance().apply { time = calDate }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        mgr.saveReminder(Reminder(
            id = meal.id,
            title = "Meal: ${meal.name}",
            type = ReminderType.MEAL,
            hour = meal.hour,
            minute = meal.minute,
            repeatDays = listOf(dayOfWeek),
            isHidden = true,
            dishType = meal.name,
            ingredients = meal.description
        ))
    }

    // --- Legacy Challenge Management (If needed, can be refactored to use dates) ---

    fun getWorkoutPlan(duration: ChallengeDuration): WorkoutPlan {
        // This could be deprecated or refactored to return a challenge template
        return WorkoutPlan()
    }

    fun saveWorkoutPlan(plan: WorkoutPlan) {
        // Logic for saving a whole plan/challenge
    }

    private fun parseDate(date: String): Calendar {
        val parsed = dateFormatter.parse(date) ?: Date()
        return Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun formatDate(calendar: Calendar): String = dateFormatter.format(calendar.time)

    private fun addDays(calendar: Calendar, days: Int): Calendar {
        return (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) }
    }

    private fun daysBetweenInclusive(start: Calendar, end: Calendar): Int {
        val millis = end.timeInMillis - start.timeInMillis
        return (millis / (24L * 60L * 60L * 1000L)).toInt() + 1
    }

    private fun findOverlap(
        ranges: List<AppliedTemplateRange>,
        desiredStart: Calendar,
        desiredEnd: Calendar
    ): AppliedTemplateRange? {
        return ranges.firstOrNull { range ->
            val rangeStart = parseDate(range.startDate)
            val rangeEnd = parseDate(range.endDate)
            !desiredEnd.before(rangeStart) && !desiredStart.after(rangeEnd)
        }
    }

    private fun generateItemId(): Int {
        return (System.currentTimeMillis() % Int.MAX_VALUE).toInt() + Random().nextInt(1000)
    }
}
