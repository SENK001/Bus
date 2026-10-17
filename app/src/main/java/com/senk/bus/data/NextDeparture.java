package com.senk.bus.data;

import com.senk.bus.data.entity.Schedule;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure-Java helpers for "next departure" calculations on {@code HH:mm} strings.
 *
 * <p>All times are local wall-clock times. Input lists are expected to be sorted ascending by
 * {@code departureTime}; the DAOs guarantee this with {@code ORDER BY departureTime ASC}.
 *
 * <p>Kept free of Android dependencies so it can be unit-tested on the JVM.
 */
public final class NextDeparture {

    /** Strict {@code HH:mm} matcher, 00:00..23:59. */
    public static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private static final DateTimeFormatter HH_MM =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final int MINUTES_PER_DAY = 24 * 60;

    /** Next departure time, formatted {@code HH:mm}. */
    public final String time;

    /** Minutes from "now" until {@link #time}, in {@code 0..1439}; wraps to the next day. */
    public final int remainingMinutes;

    /** The schedule entry that {@link #time} belongs to. */
    public final Schedule schedule;

    private NextDeparture(String time, int remainingMinutes, Schedule schedule) {
        this.time = time;
        this.remainingMinutes = remainingMinutes;
        this.schedule = schedule;
    }

    /** Current local time as {@code HH:mm} (locale independent, stable for comparisons). */
    public static String nowHHmm() {
        return LocalTime.now().format(HH_MM);
    }

    /** Formats a minute-of-day value into {@code HH:mm}, wrapping into {@code 0..1439}. */
    public static String format(int minutesOfDay) {
        int normalized = ((minutesOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        return String.format(Locale.ROOT, "%02d:%02d", normalized / 60, normalized % 60);
    }

    /** Returns {@code true} when the value is a valid {@code HH:mm} time string. */
    public static boolean isValid(String hhmm) {
        return hhmm != null && TIME_PATTERN.matcher(hhmm).matches();
    }

    /** Parses {@code HH:mm} into a minute-of-day value; returns {@code -1} when invalid. */
    public static int parseMinutes(String hhmm) {
        if (!isValid(hhmm)) {
            return -1;
        }
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    /**
     * Returns the minutes from {@code fromHHmm} to {@code toHHmm}, wrapping into {@code 0..1439}.
     * Returns {@code -1} when either argument is invalid.
     */
    public static int minutesUntil(String fromHHmm, String toHHmm) {
        int from = parseMinutes(fromHHmm);
        int to = parseMinutes(toHHmm);
        if (from < 0 || to < 0) {
            return -1;
        }
        int diff = to - from;
        if (diff < 0) {
            diff += MINUTES_PER_DAY;
        }
        return diff;
    }

    /**
     * Finds the next departure at or after {@code nowHHmm}.
     *
     * <p>Invalid entries are skipped. When every valid entry has already departed, the earliest
     * valid entry is returned as tomorrow's first departure (with wrapped remaining minutes).
     *
     * @param nowHHmm            current local time as {@code HH:mm}; may be null/invalid, in which
     *                           case the current wall-clock time is used
     * @param schedulesAscending schedules sorted ascending by departure time; may be null/empty
     * @return the next departure, or {@code null} when no valid schedule exists
     */
    public static NextDeparture find(String nowHHmm, List<Schedule> schedulesAscending) {
        if (schedulesAscending == null || schedulesAscending.isEmpty()) {
            return null;
        }
        int now = parseMinutes(nowHHmm);
        if (now < 0) {
            now = parseMinutes(nowHHmm());
        }

        Schedule earliest = null;
        int earliestMinutes = Integer.MAX_VALUE;
        Schedule upcoming = null;
        int upcomingMinutes = Integer.MAX_VALUE;
        for (Schedule schedule : schedulesAscending) {
            if (schedule == null) {
                continue;
            }
            int candidate = parseMinutes(schedule.getDepartureTime());
            if (candidate < 0) {
                continue;
            }
            if (candidate < earliestMinutes) {
                earliestMinutes = candidate;
                earliest = schedule;
            }
            if (candidate >= now && candidate < upcomingMinutes) {
                upcomingMinutes = candidate;
                upcoming = schedule;
            }
        }
        if (upcoming != null) {
            return new NextDeparture(upcoming.getDepartureTime(), upcomingMinutes - now, upcoming);
        }
        if (earliest == null) {
            return null;
        }
        return new NextDeparture(earliest.getDepartureTime(),
                earliestMinutes - now + MINUTES_PER_DAY, earliest);
    }
}
