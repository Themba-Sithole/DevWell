package za.co.devwell.model

import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoUnit
import java.util.Locale

object ProjectDeadlineParser {
    private const val INVALID_MESSAGE = "Deadline must use yyyy-MM-dd, d MMM, or d MMM yyyy"

    private val monthFormat: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("MMM")
        .toFormatter(Locale.ENGLISH)

    private val isoFormat = DateTimeFormatter.ISO_LOCAL_DATE
    private val isoPattern = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val whitespace = Regex("\\s+")

    private val displayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val displayWithYearFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /**
     * Parses a stored/display deadline into a date.
     *
     * Accepts `yyyy-MM-dd`, `d MMM` and `d MMM yyyy` where the month is an
     * English abbreviation (case-insensitive, optional trailing dot). Whitespace
     * is collapsed and impossible calendar dates are rejected.
     *
     * @throws IllegalArgumentException when the label is not a valid deadline.
     */
    fun parse(label: String, today: LocalDate = LocalDate.now()): LocalDate {
        val trimmed = label.trim().replace(whitespace, " ")
        if (trimmed.matches(isoPattern)) {
            return runCatching { LocalDate.parse(trimmed, isoFormat) }
                .getOrElse { throw IllegalArgumentException(INVALID_MESSAGE) }
        }

        val parts = trimmed.split(" ").filter { it.isNotEmpty() }
        if (parts.size != 2 && parts.size != 3) throw IllegalArgumentException(INVALID_MESSAGE)

        val day = parts[0].toIntOrNull()
            ?: throw IllegalArgumentException(INVALID_MESSAGE)
        if (day !in 1..31) throw IllegalArgumentException(INVALID_MESSAGE)

        val month = runCatching { Month.from(monthFormat.parse(parts[1].removeSuffix("."))) }
            .getOrElse { throw IllegalArgumentException(INVALID_MESSAGE) }

        val year = if (parts.size == 3) {
            val parsedYear = parts[2].toIntOrNull()
            if (parsedYear == null || parsedYear !in 1900..2999) throw IllegalArgumentException(INVALID_MESSAGE)
            parsedYear
        } else {
            if (today.month == Month.DECEMBER && month == Month.JANUARY) today.year + 1 else today.year
        }

        return runCatching { LocalDate.of(year, month, day) }
            .getOrElse { throw IllegalArgumentException(INVALID_MESSAGE) }
    }

    fun toStorage(label: String, today: LocalDate = LocalDate.now()): String =
        parse(label, today).format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun isValid(label: String): Boolean = runCatching { parse(label) }.isSuccess

    fun toDisplay(iso: String, today: LocalDate = LocalDate.now()): String {
        val date = LocalDate.parse(iso.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        val formatter = if (date.year == today.year) displayFormat else displayWithYearFormat
        return date.format(formatter)
    }

    /**
     * Days from today until the deadline; negative when overdue, null when unparseable.
     */
    fun daysUntil(label: String, today: LocalDate = LocalDate.now()): Long? =
        runCatching { ChronoUnit.DAYS.between(today, parse(label, today)) }.getOrNull()

    fun isOverdue(label: String, today: LocalDate = LocalDate.now()): Boolean =
        daysUntil(label, today)?.let { it < 0 } ?: false
}
