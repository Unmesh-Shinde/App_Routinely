package com.dailyroutine.app

import java.util.Calendar

object HistoryDateOrder {
    data class MonthWeek(
        val start: Calendar,
        val end: Calendar,
        val dates: List<Calendar>,
        val isComplete: Boolean
    )

    fun monthBoundedWeeklyGroups(historyDays: Int): List<List<Calendar>> {
        val today = Calendar.getInstance().startOfDay()
        val oldestAllowed = (today.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -(historyDays - 1))
        }
        val result = mutableListOf<List<Calendar>>()

        var monthCursor = (today.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            startOfDay()
        }
        val oldestMonth = (oldestAllowed.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            startOfDay()
        }

        while (!monthCursor.before(oldestMonth)) {
            val monthStart = (monthCursor.clone() as Calendar)
            val monthEnd = (monthCursor.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                startOfDay()
            }

            val firstDay = maxCalendar(monthStart, oldestAllowed)
            val lastDay = if (sameMonth(monthCursor, today)) {
                minCalendar(monthEnd, endOfCurrentMonthBoundedWeek(today))
            } else {
                minCalendar(monthEnd, today)
            }
            val monthGroups = buildMonthGroups(firstDay, lastDay)

            if (sameMonth(monthCursor, today)) {
                result.addAll(monthGroups.asReversed())
            } else {
                result.addAll(monthGroups)
            }

            monthCursor.add(Calendar.MONTH, -1)
        }

        return result
    }

    fun monthWeeksForMonthlyView(monthInView: Calendar, todayInput: Calendar = Calendar.getInstance()): List<MonthWeek> {
        val today = (todayInput.clone() as Calendar).startOfDay()
        val firstDay = (monthInView.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            startOfDay()
        }
        val lastDay = (firstDay.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            startOfDay()
        }

        return buildMonthGroups(firstDay, lastDay).map { dates ->
            val start = dates.first().clone() as Calendar
            val end = dates.last().clone() as Calendar
            MonthWeek(
                start = start,
                end = end,
                dates = dates,
                isComplete = !end.after(today)
            )
        }
    }

    private fun buildMonthGroups(firstDay: Calendar, lastDay: Calendar): List<List<Calendar>> {
        if (firstDay.after(lastDay)) return emptyList()

        val groups = mutableListOf<List<Calendar>>()
        var cursor = (firstDay.clone() as Calendar)

        while (!cursor.after(lastDay)) {
            val group = mutableListOf<Calendar>()
            val isFirstGroupOfMonth = cursor.get(Calendar.DAY_OF_MONTH) == 1

            while (!cursor.after(lastDay)) {
                group.add(cursor.clone() as Calendar)

                val isSunday = cursor.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                val isMonthEnd = cursor.get(Calendar.DAY_OF_MONTH) == cursor.getActualMaximum(Calendar.DAY_OF_MONTH)
                cursor.add(Calendar.DAY_OF_YEAR, 1)

                if (isSunday || isMonthEnd) break
                if (!isFirstGroupOfMonth && group.size == 7) break
            }

            groups.add(group)
        }

        return groups
    }

    private fun Calendar.startOfDay(): Calendar = apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun sameMonth(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)

    private fun endOfCurrentMonthBoundedWeek(today: Calendar): Calendar {
        val end = today.clone() as Calendar
        while (end.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY &&
            end.get(Calendar.DAY_OF_MONTH) != end.getActualMaximum(Calendar.DAY_OF_MONTH)
        ) {
            end.add(Calendar.DAY_OF_YEAR, 1)
        }
        return end.startOfDay()
    }

    private fun maxCalendar(a: Calendar, b: Calendar): Calendar = if (a.before(b)) b.clone() as Calendar else a.clone() as Calendar

    private fun minCalendar(a: Calendar, b: Calendar): Calendar = if (a.after(b)) b.clone() as Calendar else a.clone() as Calendar
}
