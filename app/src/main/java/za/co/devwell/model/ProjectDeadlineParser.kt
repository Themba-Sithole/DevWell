package za.co.devwell.model

import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.Locale

object ProjectDeadlineParser {
    private val monthFormat = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val displayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val displayWithYearFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun parse(label: String, today: LocalDate = LocalDate.now()): LocalDate {
        val trimmed = label.trim()
        if (trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
        }
        val parts = trimmed.split(Regex("\\s+"))
        require(parts.size == 2 || parts.size == 3) { "Deadline must use yyyy-MM-dd, d MMM, or d MMM yyyy" }
        val day = parts[0].toInt()
        val month = Month.from(monthFormat.parse(parts[1]))
        if (parts.size == 3) return LocalDate.of(parts[2].toInt(), month, day)
        val year = if (today.month == Month.DECEMBER && month == Month.JANUARY) today.year + 1 else today.year
        return LocalDate.of(year, month, day)
    }

    fun toStorage(label: String, today: LocalDate = LocalDate.now()): String =
        parse(label, today).format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun toDisplay(iso: String, today: LocalDate = LocalDate.now()): String {
        val date = LocalDate.parse(iso.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        val formatter = if (date.year == today.year) displayFormat else displayWithYearFormat
        return date.format(formatter)
    }
}
