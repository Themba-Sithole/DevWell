package za.co.devwell.model

import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * FR13 / Story 8 — rule-based tips only (max 3).
 * Member 4 owns expanding and testing this.
 */
object SmartTipsEngine {
    fun generate(projects: List<DevProject>, today: LocalDate = LocalDate.now()): List<SmartTip> {
        val openTasks = projects.flatMap { project ->
            project.tasks.filter { !it.isDone }.map { project to it }
        }
        val tips = mutableListOf<SmartTip>()

        val overdue = openTasks.count { (_, task) -> daysUntil(task.deadline, today) < 0 }
        if (overdue > 0) {
            tips += SmartTip("overdue", "You have $overdue overdue open task(s). Clear those first.")
        }

        val dueSoon = openTasks
            .filter { (_, task) -> daysUntil(task.deadline, today) in 0..2 }
            .minByOrNull { (_, task) -> daysUntil(task.deadline, today) }
        if (dueSoon != null) {
            tips += SmartTip(
                "deadline",
                "Prioritise \"${dueSoon.second.title}\" — deadline is ${dueSoon.second.deadline}."
            )
        }

        val highPriorityOpen = openTasks.count { (_, task) ->
            task.priority == TaskPriority.HIGH
        }
        if (highPriorityOpen > 0) {
            tips += SmartTip(
                "priority",
                "You have $highPriorityOpen high-priority open task(s). Protect a focus block today."
            )
        }

        val lowProgress = projects
            .filter { it.tasks.isNotEmpty() && it.progress < 50 }
            .minByOrNull { it.progress }
        if (lowProgress != null) {
            tips += SmartTip(
                "progress",
                "\"${lowProgress.name}\" is at ${lowProgress.progress}%. Break remaining work into smaller tasks."
            )
        }

        return tips.take(3)
    }

    private fun daysUntil(deadlineLabel: String, today: LocalDate): Long {
        return try {
            val parsed = parseDeadline(deadlineLabel, today)
            ChronoUnit.DAYS.between(today, parsed)
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun parseDeadline(deadlineLabel: String, today: LocalDate): LocalDate {
        val trimmed = deadlineLabel.trim()
        if (trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
        }
        val monthDay = MonthDay.parse(trimmed, DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        if (today.month == java.time.Month.DECEMBER && monthDay.month == java.time.Month.JANUARY) {
            return LocalDate.of(today.year + 1, monthDay.month, monthDay.dayOfMonth)
        }
        return LocalDate.of(today.year, monthDay.month, monthDay.dayOfMonth)
    }
}
