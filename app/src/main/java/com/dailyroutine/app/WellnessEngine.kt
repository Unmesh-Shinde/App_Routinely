package com.dailyroutine.app

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object WellnessEngine {

    private enum class MetConfidence { HIGH, MEDIUM, LOW }

    private enum class ExerciseCategory {
        STRENGTH_BODYWEIGHT,
        STRENGTH_WEIGHTED,
        CARDIO,
        HIIT,
        ISOMETRIC,
        MOBILITY,
        WARMUP,
        UNKNOWN
    }

    private data class LocalMetResult(
        val met: Double,
        val category: ExerciseCategory,
        val confidence: MetConfidence,
        val source: String
    )

    private data class WorkRestMinutes(
        val activeMinutes: Double,
        val restMinutes: Double
    ) {
        val totalMinutes: Double get() = activeMinutes + restMinutes
    }

    fun calculateBMR(context: Context): Double {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return calculateBMRForDate(context, todayStr)
    }

    fun calculateBMRForDate(context: Context, date: String): Double {
        val weight = HealthDataManager(context).getWeight(date)
            .let { if (it > 0) it else 70.0 }
        val height = UserPreferencesStore.getUserHeight(context)
        val age = UserPreferencesStore.getUserAge(context)
        val gender = UserPreferencesStore.getUserGender(context)

        return calculateBMRRaw(weight, height, age, gender)
    }

    suspend fun calculateBMRForDateRoom(context: Context, date: String, metrics: DailyHealthMetricEntity? = null): Double {
        val weight = metrics?.weightKg ?: HealthDataManager(context).getWeightRoom(date)
            .let { if (it > 0) it else 70.0 }
        val height = UserPreferencesStore.getUserHeight(context)
        val age = UserPreferencesStore.getUserAge(context)
        val gender = UserPreferencesStore.getUserGender(context)

        return calculateBMRRaw(weight, height, age, gender)
    }

    private fun calculateBMRRaw(weight: Double, height: Double, age: Int, gender: String?): Double {
        return if (gender == "Male") {
            (10 * weight) + (6.25 * height) - (5 * age) + 5
        } else {
            (10 * weight) + (6.25 * height) - (5 * age) - 161
        }
    }

    /**
     * Thermic Effect of Food (TEF) - roughly 10% of caloric intake is burned during digestion.
     */
    fun calculateTEF(intakeCalories: Int): Double {
        return intakeCalories * 0.10
    }

    data class AdaptiveInsight(val message: String, val type: String)

    suspend fun getAdaptiveCalorieInsightRoom(context: Context): AdaptiveInsight? {
        val hdm = HealthDataManager(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        
        val today = sdf.format(cal.time)
        val twentyOneDaysAgo = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -21) }
        val startRange = sdf.format(twentyOneDaysAgo.time)
        
        val metricsMap = hdm.getMetricsMap(startRange, today)

        // Get last 21 days of weight data
        val weights = mutableListOf<Double>()
        for (i in 0 until 21) {
            val date = sdf.format(cal.time)
            val w = metricsMap[date]?.weightKg ?: hdm.getWeight(date)
            if (w > 0) weights.add(w)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        // We need at least 10 data points across 21 days for a reliable trend
        if (weights.size < 10) return null 

        // Since we iterated backwards from today:
        // weights.take(7) is the most recent week
        // weights.takeLast(7) is the oldest week in the 21-day window
        val recentAvg = weights.take(7).average()
        val olderAvg = weights.takeLast(7).average()
        val trend = recentAvg - olderAvg

        val goal = UserPreferencesStore.getUserGoal(context)
        
        return when {
            goal == "Lose Weight" && trend > -0.1 -> {
                AdaptiveInsight("Your weight trend is stable. To reach your goal, consider reducing your daily target by 100-200 kcal.", "neutral")
            }
            goal == "Lose Weight" && trend <= -0.1 -> {
                AdaptiveInsight("Great job! You are on a downward weight trend. Keep up your current routine.", "positive")
            }
            goal == "Gain Weight" && trend < 0.1 -> {
                AdaptiveInsight("Your weight trend is stable. To gain mass, consider increasing your intake target by 200 kcal.", "neutral")
            }
            goal == "Gain Weight" && trend >= 0.1 -> {
                AdaptiveInsight("Success! You are on an upward weight trend. Maintain this caloric surplus.", "positive")
            }
            else -> null
        }
    }

    private val MET_TABLE = mapOf(
        "walking_slow" to 2.0,
        "walking_avg" to 3.5,
        "walking_brisk" to 5.0,
        "running" to 9.8,
        "calisthenics_moderate" to 3.8,
        "calisthenics_vigorous" to 8.0,
        "pushups" to 3.8,
        "pullups" to 3.8,
        "dips" to 3.8,
        "squats" to 5.0,
        "jump_squats" to 7.0,
        "lunges" to 4.5,
        "yoga" to 2.5,
        "power_yoga" to 4.0,
        "pilates" to 3.0,
        "stretching" to 2.3,
        "mobility" to 2.5,
        "weightlifting_light" to 3.0,
        "weightlifting_moderate" to 4.5,
        "weightlifting_heavy" to 6.0,
        "hiit" to 8.0,
        "cardio" to 7.0,
        "cycling" to 7.5,
        "swimming" to 7.0,
        "plank" to 2.8,
        "wall_sit" to 3.5,
        "glute_bridge" to 3.5,
        "crunches" to 3.8,
        "burpees" to 8.0,
        "jumping_jacks" to 8.0,
        "high_knees" to 8.0,
        "mountain_climbers" to 8.0,
        "skipping" to 10.0,
        "dance" to 5.5,
        "stairs" to 8.8
    )

    fun calculateActiveBurn(context: Context, steps: Int, weight: Double): Double {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return calculateActiveBurnForDate(context, todayStr, steps, weight)
    }

    fun calculateActiveBurnForDate(context: Context, date: String, steps: Int, weight: Double): Double {
        val userWeight = if (weight > 0) weight else 70.0
        val hdm = HealthDataManager(context)

        val walkingBurn = calculateWalkingBurn(hdm, date, steps, userWeight)
        val workoutBurn = calculateWorkoutBurn(context, date, userWeight)

        return walkingBurn + workoutBurn
    }

    suspend fun calculateActiveBurnForDateRoom(
        context: Context,
        date: String,
        steps: Int,
        weight: Double,
        metrics: DailyHealthMetricEntity? = null
    ): Double {
        val userWeight = if (weight > 0) weight else 70.0
        val hdm = HealthDataManager(context)

        val walkingBurn = calculateWalkingBurnRoom(hdm, date, steps, userWeight, metrics)
        val workoutBurn = calculateWorkoutBurn(context, date, userWeight)

        return walkingBurn + workoutBurn
    }

    fun calculateIntakeForDate(context: Context, date: String, onResult: (Int) -> Unit) {
        val meals = PlanManager(context).getMealsForDate(date)
        if (meals.isEmpty()) {
            onResult(0)
            return
        }

        val totalIntake = meals.sumOf { it.calories }
        Log.d("WellnessEngine", "Intake for $date: $totalIntake kcal")
        onResult(totalIntake)
    }

    private fun calculateWalkingBurn(hdm: HealthDataManager, date: String, steps: Int, weightKg: Double): Double {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val isToday = date == todayStr
        val context = hdm.contextForEngine()

        // 1. Priority: Synced Active Calories from Google Fit/Health Connect
        val syncedActiveBurn = hdm.getHistoricalActiveCalories(date)
        if (syncedActiveBurn > 0.0) {
            Log.d("WellnessEngine", "Using synced active calories for $date: $syncedActiveBurn kcal")
            return syncedActiveBurn
        }

        // 2. Fallback: Total Calories - BMR
        val totalBurned = hdm.getHistoricalCalories(date)
        if (totalBurned > 0.0) {
            val bmr = calculateBMRRaw(weightKg, UserPreferencesStore.getUserHeight(context), UserPreferencesStore.getUserAge(context), UserPreferencesStore.getUserGender(context))
            if (totalBurned > bmr) {
                return totalBurned - bmr
            }
        }

        // 3. Fallback: Calculation using continuous MET model (ACSM)
        val distanceKm = if (isToday) hdm.getDistanceKm() else {
            val histDist = hdm.getHistoricalDistance(date)
            if (histDist > 0.0) histDist else hdm.calculateDistanceKm(steps)
        }

        val moveMins = if (isToday) hdm.getMoveMinutes().toDouble() else {
            hdm.calculateDurationMin(steps).toDouble()
        }

        val heartPoints = if (isToday) hdm.getHeartPoints().toDouble() else {
            hdm.getHistoricalHeartPoints(date)
        }

        return calculateWalkingBurnRaw(hdm, weightKg, steps, distanceKm, moveMins, heartPoints)
    }

    private suspend fun calculateWalkingBurnRoom(
        hdm: HealthDataManager,
        date: String,
        steps: Int,
        weightKg: Double,
        metrics: DailyHealthMetricEntity? = null
    ): Double {
        val context = hdm.contextForEngine()
        val m = metrics ?: HealthMetricsRepository.create(context).getMetric(date)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val isToday = date == todayStr

        // 1. Priority: Direct Active Calories (Room or Prefs)
        val activeBurned = m?.activeCalories ?: hdm.getHistoricalActiveCalories(date)
        if (activeBurned > 0.0) return activeBurned

        // 2. Fallback: Total Calories - BMR
        val totalBurned = m?.totalCalories ?: hdm.getHistoricalCalories(date)
        if (totalBurned > 0.0) {
            val bmr = calculateBMRRaw(weightKg, UserPreferencesStore.getUserHeight(context), UserPreferencesStore.getUserAge(context), UserPreferencesStore.getUserGender(context))
            // Only use if it's actually greater than BMR to avoid negative active burn
            if (totalBurned > bmr) {
                return totalBurned - bmr
            }
        }

        // 3. Fallback: Calculation using continuous MET model (ACSM)
        val distanceKm = if (isToday) hdm.getDistanceKm() else {
            m?.distanceKm ?: hdm.getHistoricalDistance(date).let { if (it > 0) it else hdm.calculateDistanceKm(steps) }
        }

        val moveMins = if (isToday) hdm.getMoveMinutes().toDouble() else {
            hdm.calculateDurationMin(steps).toDouble()
        }

        val heartPoints = if (isToday) hdm.getHeartPoints().toDouble() else {
            m?.heartPoints ?: hdm.getHistoricalHeartPoints(date)
        }

        return calculateWalkingBurnRaw(hdm, weightKg, steps, distanceKm, moveMins, heartPoints)
    }

    private fun calculateWalkingBurnRaw(
        hdm: HealthDataManager,
        weightKg: Double,
        steps: Int,
        distanceKm: Double,
        moveMins: Double,
        heartPoints: Double
    ): Double {
        val baseBurn = if (moveMins > 1.0 && distanceKm > 0.05) {
            val speedKmH = distanceKm / (moveMins / 60.0)
            val met = walkingMetForSpeed(speedKmH)
            caloriesFromMet(met, weightKg, moveMins)
        } else if (steps > 0) {
            val estimatedMinutes = hdm.calculateDurationMin(steps).coerceAtLeast(1).toDouble()
            val estimatedDistance = hdm.calculateDistanceKm(steps)
            val speedKmH = estimatedDistance / (estimatedMinutes / 60.0)
            val met = walkingMetForSpeed(speedKmH)
            caloriesFromMet(met, weightKg, estimatedMinutes)
        } else {
            0.0
        }

        val heartPointsBurn = heartPoints * (weightKg / 70.0) * 4.0
        return baseBurn + heartPointsBurn
    }

    private fun calculateWorkoutBurn(context: Context, date: String, weightKg: Double): Double {
        val doneIds = RoutineProgressStore.getDoneIds(context, date)
        val doneExercises = PlanManager(context)
            .getExercisesForDate(date)
            .filter { it.id.toString() in doneIds }

        var total = 0.0
        doneExercises.forEach { exercise ->
            val burn = estimateWorkoutBurnForExercise(exercise, weightKg)
            total += burn
            Log.d("WellnessEngine", "Workout burn ${exercise.name}: $burn kcal")
        }

        return total
    }

    private fun walkingMetForSpeed(speedKmH: Double): Double {
        // ACSM Walking Equation: VO2 = (0.1 * speed_m_min) + 3.5
        // MET = VO2 / 3.5
        val speedMetersPerMin = (speedKmH * 1000.0) / 60.0
        val vo2 = (0.1 * speedMetersPerMin) + 3.5
        val met = vo2 / 3.5
        
        // Clamp MET between 2.0 (slow walk) and 5.0 (brisk walk) for general walking activity
        // higher intensity would likely be captured by Exercise records or Heart Points
        return met.coerceIn(2.0, 5.0)
    }

    private fun caloriesFromMet(met: Double, weightKg: Double, minutes: Double): Double {
        return (met * 3.5 * weightKg / 200.0) * minutes
    }

    fun estimateWorkoutBurnForExercise(exercise: Exercise, weightKg: Double): Double {
        val userWeight = if (weightKg > 0) weightKg else 70.0
        val localResult = classifyLocalWorkoutMet(exercise)
        val workRest = estimateExerciseWorkRestMinutes(exercise, localResult.category)
        val baseMet = metFromCardioDistance(exercise, workRest.activeMinutes, localResult.category)
            ?: exercise.estimatedMet
            .takeIf { isValidEnrichedWorkoutMet(exercise, it) }
            ?: localResult.met
        val adjustedMet = adjustMetForIntensity(baseMet, exercise.intensity, localResult.category)
        val activeBurn = caloriesFromMet(adjustedMet, userWeight, workRest.activeMinutes)
        val restBurn = caloriesFromMet(restMetForCategory(localResult.category), userWeight, workRest.restMinutes)
        return (activeBurn + restBurn).coerceAtLeast(0.0)
    }

    fun shouldEnrichWorkoutMet(ex: Exercise): Boolean {
        if (ex.estimatedMet > 0.0) return false
        if (isTooVagueForAi(ex.name)) return false
        return classifyLocalWorkoutMet(ex).confidence != MetConfidence.HIGH
    }

    fun getLocalWorkoutMet(ex: Exercise): Double {
        return classifyLocalWorkoutMet(ex).met
    }

    fun isValidEnrichedWorkoutMet(ex: Exercise, met: Double): Boolean {
        if (met !in 1.0..15.0) return false
        val category = classifyLocalWorkoutMet(ex).category
        val range = plausibleMetRangeFor(category)
        return met in range.first..range.second
    }

    private fun metForExercise(ex: Exercise): Double {
        return classifyLocalWorkoutMet(ex).met
    }

    private fun classifyLocalWorkoutMet(ex: Exercise): LocalMetResult {
        val name = normalizedExerciseText("${ex.name} ${ex.targetArea}")
        val exerciseName = normalizedExerciseText(ex.name)
        val structuredCategory = categoryFromExerciseType(ex.exerciseType)
        return when {
            structuredCategory == ExerciseCategory.STRENGTH_WEIGHTED -> local(weightliftingMetForIntensity(ex.intensity), ExerciseCategory.STRENGTH_WEIGHTED, MetConfidence.HIGH, "local_type_weighted")
            structuredCategory == ExerciseCategory.CARDIO && isTooVagueForAi(ex.name) -> local(MET_TABLE["cardio"]!!, ExerciseCategory.CARDIO, MetConfidence.HIGH, "local_type_cardio")
            structuredCategory == ExerciseCategory.HIIT && isTooVagueForAi(ex.name) -> local(MET_TABLE["hiit"]!!, ExerciseCategory.HIIT, MetConfidence.HIGH, "local_type_hiit")
            structuredCategory == ExerciseCategory.ISOMETRIC && isTooVagueForAi(ex.name) -> local(MET_TABLE["plank"]!!, ExerciseCategory.ISOMETRIC, MetConfidence.HIGH, "local_type_isometric")
            structuredCategory == ExerciseCategory.MOBILITY && isTooVagueForAi(ex.name) -> local(MET_TABLE["stretching"]!!, ExerciseCategory.MOBILITY, MetConfidence.HIGH, "local_type_mobility")
            structuredCategory == ExerciseCategory.WARMUP && isTooVagueForAi(ex.name) -> local(MET_TABLE["mobility"]!!, ExerciseCategory.WARMUP, MetConfidence.HIGH, "local_type_warmup")
            containsAny(name, "burpee") -> local(MET_TABLE["burpees"]!!, ExerciseCategory.HIIT, MetConfidence.HIGH, "local_exact")
            containsAny(name, "mountain climber", "mountain climbers") -> local(MET_TABLE["mountain_climbers"]!!, ExerciseCategory.HIIT, MetConfidence.HIGH, "local_exact")
            containsAny(name, "high knee", "high knees") -> local(MET_TABLE["high_knees"]!!, ExerciseCategory.HIIT, MetConfidence.HIGH, "local_exact")
            containsAny(name, "jumping jack", "jumping jacks") -> local(MET_TABLE["jumping_jacks"]!!, ExerciseCategory.CARDIO, MetConfidence.HIGH, "local_exact")
            containsAny(name, "jump rope", "skipping", "skip rope") -> local(MET_TABLE["skipping"]!!, ExerciseCategory.CARDIO, MetConfidence.HIGH, "local_exact")
            containsAny(name, "walk", "walking") -> local(MET_TABLE["walking_avg"]!!, ExerciseCategory.CARDIO, MetConfidence.HIGH, "local_exact")
            containsAny(name, "run", "running", "jog", "jogging") -> local(MET_TABLE["running"]!!, ExerciseCategory.CARDIO, MetConfidence.HIGH, "local_exact")
            containsAny(name, "cycle", "cycling", "bike", "biking") -> local(MET_TABLE["cycling"]!!, ExerciseCategory.CARDIO, MetConfidence.HIGH, "local_exact")
            containsAny(name, "swim", "swimming") -> local(MET_TABLE["swimming"]!!, ExerciseCategory.CARDIO, MetConfidence.HIGH, "local_exact")
            containsAny(name, "tabata", "hiit") -> local(MET_TABLE["hiit"]!!, ExerciseCategory.HIIT, MetConfidence.HIGH, "local_category")
            containsAny(name, "cardio", "aerobic") -> local(MET_TABLE["cardio"]!!, ExerciseCategory.CARDIO, MetConfidence.HIGH, "local_category")
            containsAny(name, "jump squat", "jump squats") -> local(MET_TABLE["jump_squats"]!!, ExerciseCategory.HIIT, MetConfidence.HIGH, "local_exact")
            containsAny(name, "push up", "push ups", "pushup", "pushups") -> local(MET_TABLE["pushups"]!!, ExerciseCategory.STRENGTH_BODYWEIGHT, MetConfidence.HIGH, "local_exact")
            containsAny(name, "pull up", "pull ups", "pullup", "pullups", "chin up", "chin ups") -> local(MET_TABLE["pullups"]!!, ExerciseCategory.STRENGTH_BODYWEIGHT, MetConfidence.HIGH, "local_exact")
            containsAny(name, "dip", "dips") -> local(MET_TABLE["dips"]!!, ExerciseCategory.STRENGTH_BODYWEIGHT, MetConfidence.HIGH, "local_exact")
            containsAny(name, "squat", "squats") -> local(MET_TABLE["squats"]!!, ExerciseCategory.STRENGTH_BODYWEIGHT, MetConfidence.HIGH, "local_exact")
            containsAny(name, "lunge", "lunges") -> local(MET_TABLE["lunges"]!!, ExerciseCategory.STRENGTH_BODYWEIGHT, MetConfidence.HIGH, "local_exact")
            containsAny(name, "plank", "side plank") -> local(MET_TABLE["plank"]!!, ExerciseCategory.ISOMETRIC, MetConfidence.HIGH, "local_exact")
            containsAny(name, "wall sit", "wall sits") -> local(MET_TABLE["wall_sit"]!!, ExerciseCategory.ISOMETRIC, MetConfidence.HIGH, "local_exact")
            containsAny(name, "glute bridge", "bridge hold") -> local(MET_TABLE["glute_bridge"]!!, ExerciseCategory.ISOMETRIC, MetConfidence.HIGH, "local_exact")
            containsAny(name, "crunch", "crunches", "sit up", "sit ups", "dead bug") -> local(MET_TABLE["crunches"]!!, ExerciseCategory.STRENGTH_BODYWEIGHT, MetConfidence.HIGH, "local_exact")
            containsAny(name, "power yoga", "vinyasa", "ashtanga") -> local(MET_TABLE["power_yoga"]!!, ExerciseCategory.MOBILITY, MetConfidence.MEDIUM, "local_category")
            containsAny(name, "yoga") -> local(MET_TABLE["yoga"]!!, ExerciseCategory.MOBILITY, MetConfidence.HIGH, "local_exact")
            containsAny(name, "pilates") -> local(MET_TABLE["pilates"]!!, ExerciseCategory.MOBILITY, MetConfidence.HIGH, "local_exact")
            containsAny(name, "stretch", "stretching", "cooldown", "cool down") -> local(MET_TABLE["stretching"]!!, ExerciseCategory.MOBILITY, MetConfidence.HIGH, "local_exact")
            containsAny(name, "mobility", "warmup", "warm up", "dynamic warm") -> local(MET_TABLE["mobility"]!!, ExerciseCategory.WARMUP, MetConfidence.MEDIUM, "local_category")
            containsAny(name, "dance", "zumba") -> local(MET_TABLE["dance"]!!, ExerciseCategory.CARDIO, MetConfidence.HIGH, "local_exact")
            containsAny(name, "stair", "stairs", "step up", "step ups") -> local(MET_TABLE["stairs"]!!, ExerciseCategory.CARDIO, MetConfidence.HIGH, "local_exact")
            isWeightedStrengthExercise(name) -> local(weightliftingMetForIntensity(ex.intensity), ExerciseCategory.STRENGTH_WEIGHTED, MetConfidence.MEDIUM, "local_weighted_fallback")
            isCircuitLikeExercise(exerciseName) -> local(MET_TABLE["hiit"]!!, ExerciseCategory.HIIT, MetConfidence.MEDIUM, "local_circuit_fallback")
            structuredCategory != null -> local(defaultMetForStructuredCategory(structuredCategory, ex.intensity), structuredCategory, MetConfidence.HIGH, "local_type_fallback")
            isTooVagueForAi(ex.name) -> local(genericMetForIntensity(ex.intensity), ExerciseCategory.UNKNOWN, MetConfidence.LOW, "local_generic_fallback")
            else -> local(genericMetForIntensity(ex.intensity), ExerciseCategory.UNKNOWN, MetConfidence.LOW, "local_generic_fallback")
        }
    }

    private fun hasSpecificExerciseMatch(ex: Exercise): Boolean {
        return classifyLocalWorkoutMet(ex).confidence == MetConfidence.HIGH
    }

    private fun local(met: Double, category: ExerciseCategory, confidence: MetConfidence, source: String): LocalMetResult {
        return LocalMetResult(met, category, confidence, source)
    }

    private fun normalizedExerciseText(text: String): String {
        return text.lowercase(Locale.US)
            .replace(Regex("""[-_/]+"""), " ")
            .replace(Regex("""[^a-z0-9\s]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun containsAny(text: String, vararg tokens: String): Boolean {
        return tokens.any { token -> Regex("""(^|\s)${Regex.escape(token)}(\s|$)""").containsMatchIn(text) }
    }

    private fun isWeightedStrengthExercise(name: String): Boolean {
        return containsAny(
            name,
            "weightlifting",
            "weight lifting",
            "weighted",
            "barbell",
            "dumbbell",
            "kettlebell",
            "deadlift",
            "bench press",
            "push press",
            "shoulder press",
            "overhead press",
            "row",
            "curl",
            "clean",
            "snatch",
            "thruster",
            "swing",
            "farmer carry",
            "lift"
        )
    }

    private fun isCircuitLikeExercise(name: String): Boolean {
        return containsAny(name, "circuit", "finisher", "conditioning", "emom", "amrap", "complex")
    }

    private fun isTooVagueForAi(name: String): Boolean {
        val normalized = normalizedExerciseText(name)
        return normalized.isBlank() || normalized in setOf("workout", "exercise", "training", "fitness", "routine")
    }

    private fun categoryFromExerciseType(type: String?): ExerciseCategory? {
        val normalized = normalizedExerciseText(type ?: "")
        return when {
            normalized.contains("weighted") -> ExerciseCategory.STRENGTH_WEIGHTED
            normalized.contains("bodyweight") || normalized == "strength" -> ExerciseCategory.STRENGTH_BODYWEIGHT
            normalized.contains("hiit") || normalized.contains("circuit") -> ExerciseCategory.HIIT
            normalized.contains("cardio") -> ExerciseCategory.CARDIO
            normalized.contains("timed hold") || normalized.contains("isometric") -> ExerciseCategory.ISOMETRIC
            normalized.contains("mobility") || normalized.contains("stretching") || normalized.contains("yoga") -> ExerciseCategory.MOBILITY
            normalized.contains("warmup") || normalized.contains("cooldown") -> ExerciseCategory.WARMUP
            else -> null
        }
    }

    private fun defaultMetForStructuredCategory(category: ExerciseCategory, intensity: Int): Double {
        return when (category) {
            ExerciseCategory.STRENGTH_WEIGHTED -> weightliftingMetForIntensity(intensity)
            ExerciseCategory.STRENGTH_BODYWEIGHT -> MET_TABLE["calisthenics_moderate"]!!
            ExerciseCategory.CARDIO -> MET_TABLE["cardio"]!!
            ExerciseCategory.HIIT -> MET_TABLE["hiit"]!!
            ExerciseCategory.ISOMETRIC -> MET_TABLE["plank"]!!
            ExerciseCategory.MOBILITY -> MET_TABLE["stretching"]!!
            ExerciseCategory.WARMUP -> MET_TABLE["mobility"]!!
            ExerciseCategory.UNKNOWN -> genericMetForIntensity(intensity)
        }
    }

    private fun weightliftingMetForIntensity(intensity: Int): Double {
        return when {
            intensity >= 75 -> MET_TABLE["weightlifting_heavy"]!!
            intensity >= 50 -> MET_TABLE["weightlifting_moderate"]!!
            else -> MET_TABLE["weightlifting_light"]!!
        }
    }

    private fun genericMetForIntensity(intensity: Int): Double {
        return if (intensity >= 75) MET_TABLE["weightlifting_heavy"]!! else MET_TABLE["weightlifting_light"]!!
    }

    private fun adjustMetForIntensity(baseMet: Double, intensity: Int, category: ExerciseCategory): Double {
        val pct = intensity.coerceIn(0, 100)
        val maxFactor = when (category) {
            ExerciseCategory.HIIT -> 1.40
            ExerciseCategory.CARDIO -> 1.35
            ExerciseCategory.STRENGTH_WEIGHTED,
            ExerciseCategory.STRENGTH_BODYWEIGHT -> 1.25
            ExerciseCategory.ISOMETRIC -> 1.20
            ExerciseCategory.MOBILITY,
            ExerciseCategory.WARMUP -> 1.15
            ExerciseCategory.UNKNOWN -> 1.25
        }
        val factor = if (pct <= 50) {
            0.85 + (pct / 50.0) * 0.15
        } else {
            1.0 + ((pct - 50) / 50.0) * (maxFactor - 1.0)
        }
        val adjusted = baseMet.coerceIn(1.0, 15.0) * factor
        val range = plausibleMetRangeFor(category)
        return adjusted.coerceIn(range.first, range.second)
    }

    private fun restMetForCategory(category: ExerciseCategory): Double {
        return when (category) {
            ExerciseCategory.HIIT -> 2.0
            ExerciseCategory.CARDIO -> 1.8
            ExerciseCategory.STRENGTH_WEIGHTED,
            ExerciseCategory.STRENGTH_BODYWEIGHT,
            ExerciseCategory.ISOMETRIC -> 1.5
            ExerciseCategory.MOBILITY,
            ExerciseCategory.WARMUP -> 1.4
            ExerciseCategory.UNKNOWN -> 1.5
        }
    }

    private fun metFromCardioDistance(ex: Exercise, activeMinutes: Double, category: ExerciseCategory): Double? {
        if (category != ExerciseCategory.CARDIO || ex.distanceKm <= 0.0 || activeMinutes <= 0.0) return null
        val speedKmH = ex.distanceKm / (activeMinutes / 60.0)
        val name = normalizedExerciseText(ex.name)
        return when {
            containsAny(name, "walk", "walking") -> walkingMetForSpeed(speedKmH)
            containsAny(name, "run", "running", "jog", "jogging") -> runningMetForSpeed(speedKmH)
            containsAny(name, "cycle", "cycling", "bike", "biking") -> cyclingMetForSpeed(speedKmH)
            else -> null
        }
    }

    private fun runningMetForSpeed(speedKmH: Double): Double {
        return when {
            speedKmH < 8.0 -> 8.3
            speedKmH < 9.7 -> 9.8
            speedKmH < 11.3 -> 10.5
            speedKmH < 12.9 -> 11.5
            else -> 12.3
        }
    }

    private fun cyclingMetForSpeed(speedKmH: Double): Double {
        return when {
            speedKmH < 16.0 -> 4.0
            speedKmH < 19.0 -> 6.8
            speedKmH < 22.5 -> 8.0
            speedKmH < 25.5 -> 10.0
            else -> 12.0
        }
    }

    private fun plausibleMetRangeFor(category: ExerciseCategory): Pair<Double, Double> {
        return when (category) {
            ExerciseCategory.MOBILITY -> 1.5 to 6.0
            ExerciseCategory.WARMUP -> 1.8 to 6.0
            ExerciseCategory.ISOMETRIC -> 2.0 to 5.0
            ExerciseCategory.STRENGTH_BODYWEIGHT -> 2.5 to 8.5
            ExerciseCategory.STRENGTH_WEIGHTED -> 2.5 to 8.0
            ExerciseCategory.CARDIO -> 2.0 to 12.5
            ExerciseCategory.HIIT -> 4.0 to 15.0
            ExerciseCategory.UNKNOWN -> 1.0 to 15.0
        }
    }

    private fun estimateExerciseDurationMinutes(ex: Exercise): Double {
        return estimateExerciseWorkRestMinutes(ex, classifyLocalWorkoutMet(ex).category).totalMinutes.coerceAtLeast(1.0)
    }

    private fun estimateExerciseWorkRestMinutes(ex: Exercise, category: ExerciseCategory): WorkRestMinutes {
        val repsText = ex.reps.lowercase(Locale.US).trim()
        structuredWorkRestMinutes(ex, category)?.let { return it.ensureMinimumActiveMinute() }

        val explicitMinutes = Regex("""(\d+(?:\.\d+)?)\s*(min|mins|minute|minutes)\b""")
            .find(repsText)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
        if (explicitMinutes != null) {
            val multiplier = if (treatExplicitMinutesAsTotal(category, repsText)) 1 else ex.sets.coerceAtLeast(1)
            return WorkRestMinutes(explicitMinutes * multiplier, 0.0).ensureMinimumActiveMinute()
        }

        val explicitSeconds = Regex("""(\d+(?:\.\d+)?)\s*(sec|secs|second|seconds)\b""")
            .find(repsText)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
        if (explicitSeconds != null) {
            val sets = ex.sets.coerceAtLeast(1)
            val activeMinutes = (explicitSeconds / 60.0) * sets
            val restMinutes = explicitRestMinutes(ex, sets, category)
            return WorkRestMinutes(activeMinutes, restMinutes).ensureMinimumActiveMinute()
        }

        val reps = Regex("""\d+""").find(repsText)?.value?.toIntOrNull() ?: 10
        val totalReps = reps * ex.sets.coerceAtLeast(1)
        val secondsPerRep = secondsPerRepFor(category)
        val activeMinutes = (totalReps * secondsPerRep) / 60.0
        val restMinutes = explicitRestMinutes(ex, ex.sets.coerceAtLeast(1), category)
        return WorkRestMinutes(activeMinutes, restMinutes).ensureMinimumActiveMinute()
    }

    private fun structuredWorkRestMinutes(ex: Exercise, category: ExerciseCategory): WorkRestMinutes? {
        val sets = ex.sets.coerceAtLeast(1)
        val restSeconds = ex.restSeconds.coerceIn(0, 600)
        val durationSeconds = ex.durationSeconds.coerceAtLeast(0)
        val workSeconds = ex.workSeconds.coerceAtLeast(0)
        val rounds = ex.rounds.coerceAtLeast(0)

        return when {
            category == ExerciseCategory.HIIT && rounds > 0 && workSeconds > 0 -> {
                val restBlocks = (rounds - 1).coerceAtLeast(0)
                WorkRestMinutes((rounds * workSeconds) / 60.0, (restBlocks * restSeconds) / 60.0)
            }
            category == ExerciseCategory.ISOMETRIC && durationSeconds > 0 -> {
                WorkRestMinutes((sets * durationSeconds) / 60.0, (((sets - 1).coerceAtLeast(0) * restSeconds)) / 60.0)
            }
            category in setOf(ExerciseCategory.CARDIO, ExerciseCategory.MOBILITY, ExerciseCategory.WARMUP) && durationSeconds > 0 -> {
                WorkRestMinutes(durationSeconds / 60.0, 0.0)
            }
            durationSeconds > 0 && category == ExerciseCategory.UNKNOWN -> WorkRestMinutes(durationSeconds / 60.0, 0.0)
            else -> null
        }
    }

    private fun explicitRestMinutes(ex: Exercise, sets: Int, category: ExerciseCategory): Double {
        val restBlocks = (sets - 1).coerceAtLeast(0)
        val restSeconds = if (ex.restSeconds > 0) {
            ex.restSeconds.coerceIn(0, 600)
        } else {
            defaultRestSecondsForCategory(category, ex.intensity)
        }
        return (restBlocks * restSeconds) / 60.0
    }

    private fun defaultRestSecondsForCategory(category: ExerciseCategory, intensity: Int): Int {
        return when (category) {
            ExerciseCategory.HIIT,
            ExerciseCategory.CARDIO -> 20
            ExerciseCategory.ISOMETRIC -> 45
            ExerciseCategory.STRENGTH_WEIGHTED -> if (intensity >= 75) 90 else 60
            ExerciseCategory.STRENGTH_BODYWEIGHT -> if (intensity >= 75) 60 else 45
            ExerciseCategory.MOBILITY,
            ExerciseCategory.WARMUP -> 15
            ExerciseCategory.UNKNOWN -> 45
        }
    }

    private fun WorkRestMinutes.ensureMinimumActiveMinute(): WorkRestMinutes {
        return if (totalMinutes >= 1.0) this else copy(activeMinutes = 1.0, restMinutes = 0.0)
    }

    private fun treatExplicitMinutesAsTotal(category: ExerciseCategory, repsText: String): Boolean {
        if (containsAny(normalizedExerciseText(repsText), "each", "per set", "per round")) return false
        return category == ExerciseCategory.CARDIO ||
            category == ExerciseCategory.HIIT ||
            category == ExerciseCategory.MOBILITY ||
            category == ExerciseCategory.WARMUP
    }

    private fun secondsPerRepFor(category: ExerciseCategory): Double {
        return when (category) {
            ExerciseCategory.HIIT,
            ExerciseCategory.CARDIO -> 1.5
            ExerciseCategory.ISOMETRIC -> 1.0
            ExerciseCategory.MOBILITY,
            ExerciseCategory.WARMUP -> 4.0
            else -> 3.0
        }
    }

    private fun estimatedRestMinutes(sets: Int, category: ExerciseCategory, intensity: Int): Double {
        if (sets <= 1) return 0.0
        val restSeconds = defaultRestSecondsForCategory(category, intensity)
        return ((sets - 1) * restSeconds) / 60.0
    }

    suspend fun getTrendInsightRoom(context: Context): String {
        val hdm = HealthDataManager(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        val today = sdf.format(cal.time)
        val fourteenDaysAgo = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -14) }
        val startRange = sdf.format(fourteenDaysAgo.time)
        
        val metricsMap = hdm.getMetricsMap(startRange, today)

        var currentWeekSteps = 0L
        for (i in 0 until 7) {
            val date = sdf.format(cal.time)
            currentWeekSteps += metricsMap[date]?.steps ?: hdm.getHistoricalSteps(date)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        var lastWeekSteps = 0L
        for (i in 0 until 7) {
            val date = sdf.format(cal.time)
            lastWeekSteps += metricsMap[date]?.steps ?: hdm.getHistoricalSteps(date)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        if (lastWeekSteps == 0L) return "Keep moving to start seeing your weekly trends!"

        val diff = ((currentWeekSteps - lastWeekSteps).toDouble() / lastWeekSteps) * 100
        return when {
            diff > 10 -> "You're crushing it! You walked ${diff.toInt()}% more this week."
            diff < -10 -> "A bit slower this week. Let's aim to beat last week's total!"
            else -> "Steady progress! You're maintaining a consistent pace."
        }
    }

}
