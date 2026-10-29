package com.fouad.cuttingnotebook.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The report periods from spec 10. */
enum class ReportPeriod { DAY, WEEK, MONTH, ALL }

/** A half-open-feeling but inclusive millisecond window, plus the dates it covers. */
data class DateRange(
    val fromMillis: Long,
    val toMillis: Long,
    val fromDate: LocalDate,
    val toDate: LocalDate,
)

/**
 * Report windows are built from the device's own calendar (spec 10), so a cut recorded at
 * 11pm belongs to that day and not to the next one in UTC.
 *
 * [anchor] is the day the operator is looking at — today, unless they stepped back through
 * earlier periods.
 */
fun reportRange(
    period: ReportPeriod,
    anchor: LocalDate,
    zone: ZoneId,
    earliestOperationMillis: Long?,
): DateRange {
    val (start, end) = when (period) {
        ReportPeriod.DAY -> anchor to anchor
        // The factory week is read as Saturday-to-Friday, the working week in Egypt.
        ReportPeriod.WEEK -> {
            val startOfWeek = anchor.minusDays(
                ((anchor.dayOfWeek.value - DayOfWeek.SATURDAY.value) + 7) % 7L,
            )
            startOfWeek to startOfWeek.plusDays(6)
        }

        ReportPeriod.MONTH -> anchor.withDayOfMonth(1) to
            anchor.withDayOfMonth(anchor.lengthOfMonth())

        ReportPeriod.ALL -> {
            val first = earliestOperationMillis
                ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                ?: anchor
            (if (first.isBefore(anchor)) first else anchor) to anchor
        }
    }
    return DateRange(
        fromMillis = start.atStartOfDay(zone).toInstant().toEpochMilli(),
        // End of the last day, inclusive, so a BETWEEN in SQL catches 23:59:59.999.
        toMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
        fromDate = start,
        toDate = end,
    )
}

/** Seconds to add to a UTC epoch second to land on the device's local date. */
fun localOffsetSeconds(zone: ZoneId, at: Long): Long =
    zone.rules.getOffset(Instant.ofEpochMilli(at)).totalSeconds.toLong()

fun shiftAnchor(period: ReportPeriod, anchor: LocalDate, steps: Long): LocalDate = when (period) {
    ReportPeriod.DAY -> anchor.plusDays(steps)
    ReportPeriod.WEEK -> anchor.plusWeeks(steps)
    ReportPeriod.MONTH -> anchor.plusMonths(steps)
    ReportPeriod.ALL -> anchor
}
