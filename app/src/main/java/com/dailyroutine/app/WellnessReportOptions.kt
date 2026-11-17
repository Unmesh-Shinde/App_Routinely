package com.dailyroutine.app

data class WellnessReportPeriod(val label: String, val days: Int)

object WellnessReportPeriods {
    val options: List<WellnessReportPeriod> = (15..180 step 15).map { days ->
        val label = when (days) {
            15 -> "Last 15 days"
            30 -> "1 month"
            else -> "${if (days % 30 == 0) (days / 30).toString() else "${days / 30}.5"} months"
        }
        WellnessReportPeriod(label, days)
    }
}

class ReportParameterSelection(
    initialSelection: Set<WellnessReportManager.Parameter> = WellnessReportManager.Parameter.values().toSet()
) {
    private val allParameters = WellnessReportManager.Parameter.values().toSet()
    private val chosenParameters = initialSelection.intersect(allParameters).toMutableSet()

    val selected: Set<WellnessReportManager.Parameter>
        get() = chosenParameters.toSet()

    val isAllSelected: Boolean
        get() = chosenParameters.size == allParameters.size

    val dailyParameters: Set<WellnessReportManager.Parameter>
        get() = selected.filter { it.columnLabel != null }.toSet()

    val summaryParameters: Set<WellnessReportManager.Parameter>
        get() = selected.filter { it.columnLabel == null && it != WellnessReportManager.Parameter.PROFILE_METRICS }.toSet()

    val includesProfileMetrics: Boolean
        get() = WellnessReportManager.Parameter.PROFILE_METRICS in chosenParameters

    fun setSelected(parameter: WellnessReportManager.Parameter, selected: Boolean) {
        if (selected) chosenParameters.add(parameter) else chosenParameters.remove(parameter)
    }

    fun selectAll(selected: Boolean) {
        chosenParameters.clear()
        if (selected) chosenParameters.addAll(allParameters)
    }

    fun replaceSelection(selection: Set<WellnessReportManager.Parameter>) {
        chosenParameters.clear()
        chosenParameters.addAll(selection.intersect(allParameters))
    }
}
