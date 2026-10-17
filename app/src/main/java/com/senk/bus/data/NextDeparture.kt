package com.senk.bus.data

import com.senk.bus.data.entity.Schedule
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

/**
 * Pure-Kotlin helpers for "next departure" calculations on `HH:mm` strings.
 *
 * All times are local wall-clock times. [find] is order-independent and skips invalid entries;
 * DAOs return schedules sorted ascending by departure time for display purposes only.
 *
 * Kept free of Android dependencies so it can be unit-tested on the JVM.
 */
class NextDeparture private constructor(
    /** Next departure time, formatted `HH:mm`. */
    @JvmField val time: String,
    /** Minutes from "now" until [time], in `0..1439`; wraps to the next day. */
    @JvmField val remainingMinutes: Int,
    /** The schedule entry that [time] belongs to. */
    @JvmField val schedule: Schedule,
) {

    companion object {
        /** Strict `HH:mm` matcher, 00:00..23:59. */
        @JvmField
        val TIME_PATTERN: Pattern = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$")

        private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        private const val MINUTES_PER_DAY = 24 * 60

        /** Current local time as `HH:mm` (locale independent, stable for comparisons). */
        @JvmStatic
        fun nowHHmm(): String = LocalTime.now().format(HH_MM)

        /** Formats a minute-of-day value into `HH:mm`, wrapping into `0..1439`. */
        @JvmStatic
        fun format(minutesOfDay: Int): String {
            val normalized = ((minutesOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
            return String.format(Locale.ROOT, "%02d:%02d", normalized / 60, normalized % 60)
        }

        /** Returns `true` when the value is a valid `HH:mm` time string. */
        @JvmStatic
        fun isValid(hhmm: String?): Boolean =
            hhmm != null && TIME_PATTERN.matcher(hhmm).matches()

        /** Parses `HH:mm` into a minute-of-day value; returns `-1` when invalid. */
        @JvmStatic
        fun parseMinutes(hhmm: String?): Int {
            if (!isValid(hhmm)) {
                return -1
            }
            val value = hhmm!!
            return value.substring(0, 2).toInt() * 60 + value.substring(3, 5).toInt()
        }

        /**
         * Returns the minutes from [fromHHmm] to [toHHmm], wrapping into `0..1439`.
         * Returns `-1` when either argument is invalid.
         */
        @JvmStatic
        fun minutesUntil(fromHHmm: String?, toHHmm: String?): Int {
            val from = parseMinutes(fromHHmm)
            val to = parseMinutes(toHHmm)
            if (from < 0 || to < 0) {
                return -1
            }
            var diff = to - from
            if (diff < 0) {
                diff += MINUTES_PER_DAY
            }
            return diff
        }

        /**
         * Finds the next departure at or after [nowHHmm].
         *
         * Invalid entries are skipped. When every valid entry has already departed, the earliest
         * valid entry is returned as tomorrow's first departure (with wrapped remaining minutes).
         *
         * @param nowHHmm current local time as `HH:mm`; null/invalid falls back to the current time
         * @param schedulesAscending schedules to search; may be null/empty
         * @return the next departure, or `null` when no valid schedule exists
         */
        @JvmStatic
        fun find(nowHHmm: String?, schedulesAscending: List<Schedule?>?): NextDeparture? {
            if (schedulesAscending.isNullOrEmpty()) {
                return null
            }
            var now = parseMinutes(nowHHmm)
            if (now < 0) {
                now = parseMinutes(nowHHmm())
            }

            var earliest: Schedule? = null
            var earliestMinutes = Int.MAX_VALUE
            var upcoming: Schedule? = null
            var upcomingMinutes = Int.MAX_VALUE
            for (schedule in schedulesAscending) {
                if (schedule == null) {
                    continue
                }
                val candidate = parseMinutes(schedule.departureTime)
                if (candidate < 0) {
                    continue
                }
                if (candidate < earliestMinutes) {
                    earliestMinutes = candidate
                    earliest = schedule
                }
                if (candidate >= now && candidate < upcomingMinutes) {
                    upcomingMinutes = candidate
                    upcoming = schedule
                }
            }
            if (upcoming != null) {
                return NextDeparture(upcoming.departureTime, upcomingMinutes - now, upcoming)
            }
            if (earliest == null) {
                return null
            }
            return NextDeparture(
                earliest.departureTime,
                earliestMinutes - now + MINUTES_PER_DAY,
                earliest,
            )
        }
    }
}
