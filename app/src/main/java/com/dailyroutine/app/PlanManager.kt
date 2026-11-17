package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

data class TemplateApplyResult(
    val applied: Boolean,
    val conflictRange: AppliedTemplateRange? = null,
    val appliedEndDate: String? = null,
    val failureReason: String? = null
)

internal const val MAX_MEALS_PER_CATEGORY_PER_DAY = 2

class PlanManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("plans_pref", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val mealTemplateManager by lazy {
        TemplateManager(
            lock = LOCK,
            readTemplates = ::getMealTemplates,
            writeTemplates = ::saveMealTemplates,
            readRanges = ::getAppliedMealTemplateRanges,
            writeRanges = ::saveAppliedMealTemplateRanges,
            copyWithName = { template, name -> template.copy(name = name) }
        )
    }
    private val workoutTemplateManager by lazy {
        TemplateManager(
            lock = LOCK,
            readTemplates = ::getWorkoutTemplates,
            writeTemplates = ::saveWorkoutTemplates,
            readRanges = ::getAppliedWorkoutTemplateRanges,
            writeRanges = ::saveAppliedWorkoutTemplateRanges,
            copyWithName = { template, name -> template.copy(name = name) }
        )
    }

    private companion object {
        private const val KEY_DIET_CALENDAR = "diet_calendar_data"
        private const val KEY_WORKOUT_CALENDAR = "workout_calendar_data"
        private const val KEY_MEAL_TEMPLATES = "meal_templates"
        private const val KEY_WORKOUT_TEMPLATES = "workout_templates"
        private const val KEY_APPLIED_MEAL_TEMPLATE_RANGES = "applied_meal_template_ranges"
        private const val KEY_APPLIED_WORKOUT_TEMPLATE_RANGES = "applied_workout_template_ranges"

        private val LOCK = Any()
    }

    // --- Calendar-Based Diet Plan Management ---

    @Volatile
    private var cachedDietPlan: DietPlan? = null

    fun clearDietData() {
        synchronized(LOCK) {
            cachedDietPlan = null
            prefs.edit()
                .remove(KEY_DIET_CALENDAR)
                .remove(KEY_MEAL_TEMPLATES)
                .remove(KEY_APPLIED_MEAL_TEMPLATE_RANGES)
                .apply()
        }
    }

    fun clearWorkoutData() {
        prefs.edit()
            .remove(KEY_WORKOUT_CALENDAR)
            .remove(KEY_WORKOUT_TEMPLATES)
            .remove(KEY_APPLIED_WORKOUT_TEMPLATE_RANGES)
            .apply()
    }

    private fun getFullDietPlan(): DietPlan {
        synchronized(LOCK) {
            if (cachedDietPlan != null) {
                return cachedDietPlan!!
            }
            val json = prefs.getString(KEY_DIET_CALENDAR, null)
            val plan = if (json != null) {
                val type = object : TypeToken<DietPlan>() {}.type
                gson.fromJson(json, type) ?: DietPlan()
            } else {
                DietPlan()
            }
            cachedDietPlan = plan
            return plan
        }
    }

    fun getMealsForDate(date: String): MutableList<Meal> {
        synchronized(LOCK) {
            return getFullDietPlan().dailyMeals[date] ?: mutableListOf()
        }
    }

    fun getAllDietMealsByDate(): Map<String, List<Meal>> {
        synchronized(LOCK) {
            return getFullDietPlan().dailyMeals.toMap()
        }
    }

    private fun saveFullDietPlan(plan: DietPlan) {
        synchronized(LOCK) {
            cachedDietPlan = plan
            prefs.edit().putString(KEY_DIET_CALENDAR, gson.toJson(plan)).apply()
        }
    }

    fun saveMealForDate(date: String, meal: Meal) {
        synchronized(LOCK) {
            val plan = getFullDietPlan()
            val list = plan.dailyMeals.getOrPut(date) { mutableListOf() }
            val idx = list.indexOfFirst { it.id == meal.id }
            if (idx >= 0) list[idx] = meal else list.add(meal)

            saveFullDietPlan(plan)
        }
    }

    fun deleteMealForDate(date: String, meal: Meal) {
        synchronized(LOCK) {
            val plan = getFullDietPlan()
            val removed = plan.dailyMeals[date]?.removeIf { it.id == meal.id } == true
            if (!removed) return
            if (plan.dailyMeals[date].isNullOrEmpty()) {
                plan.dailyMeals.remove(date)
            }
            saveFullDietPlan(plan)
            val actionDate = LocalDate.now().toString()
            recordMealDeleteOccurred(actionDate)
            grantReplacementCreditOnDelete(actionDate, meal.mealType)
        }
    }

    private fun aiTokenKey(date: String, mealType: String) = "ai_token_v1|$date|${mealType.lowercase().trim()}"
    private fun replacementCreditKey(date: String, mealType: String) = "replacement_credit_v1|$date|${mealType.lowercase().trim()}"
    private fun dailyEditOccurredKey(date: String) = "daily_edit_occurred|$date"
    private fun dailyDeleteOccurredKey(date: String) = "daily_delete_occurred|$date"

    fun getAiTokenCount(date: String, mealType: String): Int {
        synchronized(LOCK) {
            return prefs.getInt(aiTokenKey(date, mealType), 0)
        }
    }

    fun incrementAiTokenCount(date: String, mealType: String) {
        synchronized(LOCK) {
            val key = aiTokenKey(date, mealType)
            prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
        }
    }

    fun tryReserveAiToken(date: String, mealType: String, baseTokenLimit: Int = 2): Boolean {
        if (mealType.isBlank()) return false
        synchronized(LOCK) {
            val tokenKey = aiTokenKey(date, mealType)
            val usedTokens = prefs.getInt(tokenKey, 0)
            val maxAllowedTokens = baseTokenLimit + prefs.getInt(replacementCreditKey(date, mealType), 0)
            if (usedTokens >= maxAllowedTokens) return false
            prefs.edit().putInt(tokenKey, usedTokens + 1).apply()
            return true
        }
    }

    fun releaseAiTokenReservation(date: String, mealType: String) {
        synchronized(LOCK) {
            val key = aiTokenKey(date, mealType)
            val current = prefs.getInt(key, 0)
            if (current > 0) prefs.edit().putInt(key, current - 1).apply()
        }
    }

    fun createAiRequestBudget(date: String, mealType: String): AiRequestBudget {
        return AiRequestBudget(
            usageDate = date,
            mealType = mealType,
            reserve = { tryReserveAiToken(date, mealType) },
            release = { releaseAiTokenReservation(date, mealType) }
        )
    }

    fun getReplacementCreditCount(date: String, mealType: String): Int {
        synchronized(LOCK) {
            return prefs.getInt(replacementCreditKey(date, mealType), 0)
        }
    }

    fun grantReplacementCreditOnDelete(date: String, mealType: String) {
        if (!mealType.isBlank()) {
            synchronized(LOCK) {
                val maxAllowed = getBonusAllowedReplacementCredits(date)
                val key = replacementCreditKey(date, mealType)
                val current = prefs.getInt(key, 0)
                if (current < maxAllowed) {
                    prefs.edit().putInt(key, current + 1).apply()
                }
            }
        }
    }

    fun isAiTokenLimitReached(date: String, mealType: String, baseTokenLimit: Int = 2): Boolean {
        synchronized(LOCK) {
            val usedTokens = prefs.getInt(aiTokenKey(date, mealType), 0)
            val maxAllowedTokens = baseTokenLimit + prefs.getInt(replacementCreditKey(date, mealType), 0)
            return usedTokens >= maxAllowedTokens
        }
    }

    // --- Streak & Discipline Rewards System ---

    fun hasPrimaryMealLogged(date: String): Boolean {
        synchronized(LOCK) {
            val meals = getMealsForDate(date)
            val primaryTypes = setOf("breakfast", "lunch", "dinner")
            return meals.any { meal ->
                !meal.isWaterIntakeSummary && primaryTypes.contains(meal.mealType.lowercase().trim())
            }
        }
    }

    fun recordMealEditOccurred(date: String) {
        synchronized(LOCK) {
            prefs.edit().putBoolean(dailyEditOccurredKey(date), true).putInt("edit_streak_days", 0).apply()
        }
    }

    fun recordMealDeleteOccurred(date: String) {
        synchronized(LOCK) {
            prefs.edit().putBoolean(dailyDeleteOccurredKey(date), true).putInt("delete_streak_days", 0).apply()
        }
    }

    fun evaluateStreaks(todayDate: String) {
        synchronized(LOCK) {
            val today = runCatching { LocalDate.parse(todayDate) }.getOrElse { LocalDate.now() }
            val yesterday = today.minusDays(1)

            fun evaluateOperationStreak(
                streakKey: String,
                lastEvalKey: String,
                eventKey: (String) -> String,
                rewardType: String,
                bonusKeyPrefix: String
            ) {
                val rawLastEvaluated = prefs.getString(lastEvalKey, null)
                val parsedLastEvaluated = rawLastEvaluated?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                var streak = prefs.getInt(streakKey, 0)
                if (rawLastEvaluated != null && parsedLastEvaluated == null) streak = 0

                var date = parsedLastEvaluated?.plusDays(1) ?: yesterday
                if (date.isAfter(yesterday)) return

                while (!date.isAfter(yesterday)) {
                    val qualifies = hasPrimaryMealLogged(date.toString()) &&
                        !prefs.getBoolean(eventKey(date.toString()), false)
                    streak = if (qualifies) streak + 1 else 0

                    if (streak >= 3) {
                        // The reward is available on the day after the third qualifying day,
                        // and for the next two dates: exactly three calendar days in total.
                        val activeFrom = date.plusDays(1)
                        val activeUntil = activeFrom.plusDays(2)
                        if (!today.isBefore(activeFrom) && !today.isAfter(activeUntil)) {
                            prefs.edit()
                                .putString("${bonusKeyPrefix}_from_date", activeFrom.toString())
                                .putString("${bonusKeyPrefix}_until_date", activeUntil.toString())
                                .apply()
                            addPendingStreakPopup(rewardType)
                        }
                        streak = 0
                    }
                    date = date.plusDays(1)
                }

                prefs.edit()
                    .putInt(streakKey, streak)
                    .putString(lastEvalKey, yesterday.toString())
                    .apply()
            }

            evaluateOperationStreak(
                streakKey = "edit_streak_days",
                lastEvalKey = "last_edit_eval_date",
                eventKey = ::dailyEditOccurredKey,
                rewardType = "edit",
                bonusKeyPrefix = "edit_bonus"
            )
            evaluateOperationStreak(
                streakKey = "delete_streak_days",
                lastEvalKey = "last_delete_eval_date",
                eventKey = ::dailyDeleteOccurredKey,
                rewardType = "delete",
                bonusKeyPrefix = "delete_bonus"
            )
        }
    }

    private fun addPendingStreakPopup(type: String) {
        val existing = prefs.getString("pending_streak_popup_type", null)
        val combined = when {
            existing == null -> type
            existing == type -> type
            else -> "both"
        }
        prefs.edit().putString("pending_streak_popup_type", combined).apply()
    }

    fun consumePendingStreakPopup(): String? {
        val type = prefs.getString("pending_streak_popup_type", null)
        if (type != null) {
            prefs.edit().remove("pending_streak_popup_type").apply()
        }
        return type
    }

    fun isEditBonusActive(todayDate: String): Boolean {
        return isBonusActive("edit_bonus", todayDate)
    }

    fun isDeleteBonusActive(todayDate: String): Boolean {
        return isBonusActive("delete_bonus", todayDate)
    }

    private fun isBonusActive(keyPrefix: String, date: String): Boolean {
        val untilDate = prefs.getString("${keyPrefix}_until_date", null) ?: return false
        val activeFrom = prefs.getString("${keyPrefix}_from_date", null)
            ?: runCatching { LocalDate.parse(untilDate).minusDays(3).toString() }.getOrNull()
            ?: return false // Legacy rewards had no start date; infer their original grant date.
        return date >= activeFrom && date <= untilDate
    }

    fun getBonusAllowedEdits(todayDate: String): Int {
        return if (isEditBonusActive(todayDate)) 3 else 2
    }

    fun getBonusAllowedReplacementCredits(todayDate: String): Int {
        return if (isDeleteBonusActive(todayDate)) 2 else 1
    }

    private fun addDaysToDate(dateStr: String, days: Int): String {
        return try {
            val cal = Calendar.getInstance().apply {
                time = dateFormatter.parse(dateStr) ?: Date()
                add(Calendar.DAY_OF_YEAR, days)
            }
            dateFormatter.format(cal.time)
        } catch (_: Exception) {
            dateStr
        }
    }

    fun hasMealsForDate(date: String): Boolean {
        return !getFullDietPlan().dailyMeals[date].isNullOrEmpty()
    }

    fun getMealCountForCategory(date: String, mealType: String, excludeMealId: Int? = null): Int {
        synchronized(LOCK) {
            val meals = getMealsForDate(date)
            return meals.count { meal ->
                !meal.isWaterIntakeSummary &&
                    meal.mealType.equals(mealType, ignoreCase = true) &&
                    (excludeMealId == null || meal.id != excludeMealId)
            }
        }
    }

    fun isCategoryLimitReached(
        date: String,
        mealType: String,
        excludeMealId: Int? = null,
        maxLimit: Int = MAX_MEALS_PER_CATEGORY_PER_DAY
    ): Boolean {
        return getMealCountForCategory(date, mealType, excludeMealId) >= maxLimit
    }

    fun getPendingOvernightMeals(): List<Pair<String, Meal>> {
        synchronized(LOCK) {
            val result = mutableListOf<Pair<String, Meal>>()
            val allMeals = getAllDietMealsByDate()
            for ((date, meals) in allMeals) {
                for (meal in meals) {
                    if (!meal.isWaterIntakeSummary && meal.isPendingOvernightRetry && !meal.name.isBlank()) {
                        result.add(Pair(date, meal))
                    }
                }
            }
            return result.sortedWith(compareBy<Pair<String, Meal>> { it.second.nextNutritionRetryAt }.thenBy { it.second.addedTimestamp })
        }
    }

    fun initializeLegacyNutritionRetries(now: Long = System.currentTimeMillis()) {
        synchronized(LOCK) {
            val plan = getFullDietPlan()
            var changed = false
            for ((_, list) in plan.dailyMeals) {
                for (index in list.indices) {
                    val meal = list[index]
                    if (meal.isPendingOvernightRetry && meal.nextNutritionRetryAt <= 0L && !meal.nutritionAutoRetriesExhausted) {
                        list[index] = meal.copy(
                            nutritionRetryStage = meal.nutritionRetryStage ?: "day",
                            nextNutritionRetryAt = now,
                            nutritionDayRetriesUsed = meal.nutritionDayRetriesUsed.coerceAtMost(2)
                        )
                        changed = true
                    }
                }
            }
            if (changed) saveFullDietPlan(plan)
        }
    }

    fun getMealsWithNutritionTextErrors(): List<Pair<String, Meal>> {
        synchronized(LOCK) {
            return getAllDietMealsByDate().flatMap { (date, meals) ->
                meals.filter { !it.isWaterIntakeSummary && it.nutritionErrorCode in setOf("ai_payload_too_large", "ai_content_blocked") }
                    .map { date to it }
            }.sortedWith(
                compareBy<Pair<String, Meal>> {
                    it.second.addedTimestamp.takeIf { timestamp -> timestamp > 0L }
                        ?: (parseDate(it.first).timeInMillis + (it.second.hour * 60L + it.second.minute) * 60_000L)
                }.thenBy { it.first }.thenBy { it.second.id }
            )
        }
    }

    fun updateMealRetryState(
        date: String,
        mealId: Int,
        expectedName: String,
        expectedDescription: String,
        transform: (Meal) -> Meal
    ): Meal? {
        synchronized(LOCK) {
            val plan = getFullDietPlan()
            val list = plan.dailyMeals[date] ?: return null
            val idx = list.indexOfFirst { it.id == mealId }
            if (idx < 0) return null
            val meal = list[idx]
            if (meal.name != expectedName || meal.description != expectedDescription || !meal.isPendingOvernightRetry) return null
            val updated = transform(meal)
            list[idx] = updated
            saveFullDietPlan(plan)
            return updated
        }
    }

    fun updateMealNutrition(
        date: String,
        mealId: Int,
        info: MealNutritionInfo,
        markPendingFalse: Boolean = true,
        expectedName: String? = null,
        expectedDescription: String? = null
    ): Boolean {
        if (!info.hasAnyNutrition()) return false
        synchronized(LOCK) {
            val plan = getFullDietPlan()
            val list = plan.dailyMeals[date] ?: return false
            val idx = list.indexOfFirst { it.id == mealId }
            if (idx < 0) return false

            val existingMeal = list[idx]
            if ((expectedName != null && existingMeal.name != expectedName) ||
                (expectedDescription != null && existingMeal.description != expectedDescription) ||
                (markPendingFalse && !existingMeal.isPendingOvernightRetry)
            ) return false
            val updatedMeal = existingMeal.copy(
                calories = info.calories ?: existingMeal.calories,
                nutritionInfo = info,
                nutritionErrorCode = null,
                isPendingOvernightRetry = if (markPendingFalse) false else existingMeal.isPendingOvernightRetry,
                nutritionDayRetriesUsed = 0,
                nutritionOvernightRetriesUsed = 0,
                nutritionRetryStage = null,
                nextNutritionRetryAt = 0L,
                nutritionAutoRetriesExhausted = false,
                nutritionOvernightWindowStartAt = 0L,
                queuedTimestamp = 0L,
                manualRetryAttempted = false
            )
            list[idx] = updatedMeal
            saveFullDietPlan(plan)
            return true
        }
    }

    private fun getMealTemplates(): MutableList<MealTemplate> {
        val json = prefs.getString(KEY_MEAL_TEMPLATES, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<MealTemplate>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveMealTemplates(templates: List<MealTemplate>) {
        prefs.edit().putString(KEY_MEAL_TEMPLATES, gson.toJson(templates)).apply()
    }

    fun listMealTemplates(): List<MealTemplate> = mealTemplateManager.list()

    fun createMealTemplateFromRange(name: String, startDate: String, endDate: String, allowEmpty: Boolean): Boolean {
        return mealTemplateManager.createFromRange(
            name, startDate, endDate, allowEmpty,
            itemsForDate = ::getMealsForDate,
            copyItem = { it.copy() },
            createTemplate = { cleanName, duration, payload ->
                MealTemplate(UUID.randomUUID().toString(), cleanName, duration, payload)
            }
        )
    }

    fun renameMealTemplate(templateId: String, newName: String): Boolean {
        return mealTemplateManager.rename(templateId, newName)
    }

    fun deleteMealTemplate(templateId: String) {
        mealTemplateManager.delete(templateId)
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

    fun removeAppliedMealTemplateRange(startDate: String, endDate: String): Boolean {
        synchronized(LOCK) {
            val ranges = getAppliedMealTemplateRanges().toMutableList()
            val removed = ranges.removeAll { it.startDate == startDate && it.endDate == endDate }
            if (removed) {
                saveAppliedMealTemplateRanges(ranges)
            }
            return removed
        }
    }

    fun isDateInAppliedMealTemplateRange(date: String): Boolean {
        val day = parseDate(date)
        return getAppliedMealTemplateRanges().any { range ->
            val start = parseDate(range.startDate)
            val end = parseDate(range.endDate)
            !day.before(start) && !day.after(end)
        }
    }

    fun applyMealTemplateToRange(templateId: String, startDate: String, endDate: String): TemplateApplyResult {
        return mealTemplateManager.applyToRange(
            templateId, startDate, endDate,
            copyItem = {
                it.copy(
                    id = generateItemId(),
                    addedTimestamp = System.currentTimeMillis(),
                    queuedTimestamp = if (it.isPendingOvernightRetry) System.currentTimeMillis() else 0L,
                    manualRetryAttempted = false,
                    nutritionDayRetriesUsed = 0,
                    nutritionOvernightRetriesUsed = 0,
                    nutritionRetryStage = if (it.isPendingOvernightRetry) "day" else null,
                    nextNutritionRetryAt = if (it.isPendingOvernightRetry) System.currentTimeMillis() else 0L,
                    nutritionAutoRetriesExhausted = false,
                    nutritionOvernightWindowStartAt = 0L
                )
            },
            saveCopiedItemsByDate = { copiedByDate ->
                val plan = getFullDietPlan()
                copiedByDate.forEach { (date, meals) ->
                    plan.dailyMeals.getOrPut(date) { mutableListOf() }.addAll(meals)
                }
                saveFullDietPlan(plan)
            },
            validateCopiedItems = { copiedByDate ->
                val plan = getFullDietPlan()
                var failureReason: String? = null
                for ((date, copiedMeals) in copiedByDate) {
                    val existingMeals = plan.dailyMeals[date].orEmpty()
                        .filterNot { it.isWaterIntakeSummary }
                    val categories = (existingMeals + copiedMeals.filterNot { it.isWaterIntakeSummary })
                        .groupingBy { it.mealType.lowercase(Locale.US).trim() }
                        .eachCount()
                    val exceededCategory = categories.entries.firstOrNull {
                        it.value > MAX_MEALS_PER_CATEGORY_PER_DAY
                    }?.key
                    if (exceededCategory != null) {
                        val categoryLabel = exceededCategory.replaceFirstChar { it.uppercase(Locale.US) }
                        failureReason = "Applying this template would exceed the limit of $MAX_MEALS_PER_CATEGORY_PER_DAY $categoryLabel meals on $date. Remove or reschedule meals first."
                        break
                    }
                }
                failureReason
            }
        )
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
        synchronized(LOCK) {
            return getFullWorkoutPlan().dailyExercises[date] ?: mutableListOf()
        }
    }

    private fun saveFullWorkoutPlan(plan: WorkoutPlan) {
        prefs.edit().putString(KEY_WORKOUT_CALENDAR, gson.toJson(plan)).apply()
    }

    fun saveExerciseForDate(date: String, ex: Exercise) {
        synchronized(LOCK) {
            val plan = getFullWorkoutPlan()
            val list = plan.dailyExercises.getOrPut(date) { mutableListOf() }
            val idx = list.indexOfFirst { it.id == ex.id }
            if (idx >= 0) list[idx] = ex else list.add(ex)
            saveFullWorkoutPlan(plan)
        }
    }

    fun deleteExerciseForDate(date: String, ex: Exercise) {
        synchronized(LOCK) {
            val plan = getFullWorkoutPlan()
            plan.dailyExercises[date]?.removeIf { it.id == ex.id }
            if (plan.dailyExercises[date].isNullOrEmpty()) {
                plan.dailyExercises.remove(date)
            }
            saveFullWorkoutPlan(plan)
        }
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

    fun listWorkoutTemplates(): List<WorkoutTemplate> = workoutTemplateManager.list()

    fun createWorkoutTemplateFromRange(name: String, startDate: String, endDate: String, allowEmpty: Boolean): Boolean {
        return workoutTemplateManager.createFromRange(
            name, startDate, endDate, allowEmpty,
            itemsForDate = ::getExercisesForDate,
            copyItem = { it.copy() },
            createTemplate = { cleanName, duration, payload ->
                WorkoutTemplate(UUID.randomUUID().toString(), cleanName, duration, payload)
            }
        )
    }

    fun renameWorkoutTemplate(templateId: String, newName: String): Boolean {
        return workoutTemplateManager.rename(templateId, newName)
    }

    fun deleteWorkoutTemplate(templateId: String) {
        workoutTemplateManager.delete(templateId)
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

    fun removeAppliedWorkoutTemplateRange(startDate: String, endDate: String): Boolean {
        synchronized(LOCK) {
            val ranges = getAppliedWorkoutTemplateRanges().toMutableList()
            val removed = ranges.removeAll { it.startDate == startDate && it.endDate == endDate }
            if (removed) {
                saveAppliedWorkoutTemplateRanges(ranges)
            }
            return removed
        }
    }

    fun isDateInAppliedWorkoutTemplateRange(date: String): Boolean {
        val day = parseDate(date)
        return getAppliedWorkoutTemplateRanges().any { range ->
            val start = parseDate(range.startDate)
            val end = parseDate(range.endDate)
            !day.before(start) && !day.after(end)
        }
    }

    fun applyWorkoutTemplateToRange(templateId: String, startDate: String, endDate: String): TemplateApplyResult {
        return workoutTemplateManager.applyToRange(
            templateId, startDate, endDate,
            copyItem = { it.copy(id = generateItemId()) },
            saveCopiedItemsByDate = { copiedByDate ->
                val plan = getFullWorkoutPlan()
                copiedByDate.forEach { (date, exercises) ->
                    plan.dailyExercises.getOrPut(date) { mutableListOf() }.addAll(exercises)
                }
                saveFullWorkoutPlan(plan)
            }
        )
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

    private fun generateItemId(): Int {
        return (System.currentTimeMillis() % Int.MAX_VALUE).toInt() + Random().nextInt(1000)
    }
}
