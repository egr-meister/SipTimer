package com.egrmeister.siptimer.domain

import java.time.LocalDate

data class GoalChange(val effectiveDate: LocalDate, val goalMl: Int)

/** Effective-dated goals: the goal for a date is the latest change effective on or before it. */
class GoalHistory(changes: List<GoalChange>) {
    private val sorted = changes.sortedBy { it.effectiveDate }

    fun goalFor(date: LocalDate): Int? = sorted.lastOrNull { !it.effectiveDate.isAfter(date) }?.goalMl

    val isEmpty: Boolean get() = sorted.isEmpty()
}

object GoalMath {
    fun reached(totalMl: Long, goalMl: Int?): Boolean = goalMl != null && totalMl >= goalMl

    /** Whole-number percentage of the selected goal (may exceed 100 — shown as recorded). */
    fun percent(totalMl: Long, goalMl: Int?): Int? =
        if (goalMl == null || goalMl <= 0) null else ((totalMl * 100) / goalMl).toInt()

    fun progressFraction(totalMl: Long, goalMl: Int?): Float =
        if (goalMl == null || goalMl <= 0) 0f else (totalMl.toFloat() / goalMl).coerceIn(0f, 1f)
}
