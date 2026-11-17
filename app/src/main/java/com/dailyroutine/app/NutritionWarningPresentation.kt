package com.dailyroutine.app

enum class NutritionWarningTone { NONE, AMBER, RED }

data class NutritionWarningPresentation(
    val cardText: String?,
    val dashboardDetail: String?,
    val tone: NutritionWarningTone
) {
    companion object {
        fun forMeal(errorCode: String?, autoRetriesExhausted: Boolean = false): NutritionWarningPresentation = when (errorCode) {
            "ai_payload_too_large" -> NutritionWarningPresentation(
                cardText = "Warning: shorten meal details",
                dashboardDetail = "Meal details exceed the estimate input limit. Shorten the title or description.",
                tone = NutritionWarningTone.AMBER
            )
            "ai_content_blocked" -> NutritionWarningPresentation(
                cardText = "Warning: edit title or description",
                dashboardDetail = "Meal text was blocked by the nutrition service. Edit the title or description.",
                tone = NutritionWarningTone.RED
            )
            else -> NutritionWarningPresentation(
                cardText = if (autoRetriesExhausted) "Estimate unavailable • edit meal" else null,
                dashboardDetail = null,
                tone = if (autoRetriesExhausted) NutritionWarningTone.AMBER else NutritionWarningTone.NONE
            )
        }
    }
}
