package com.example.coffeediseasesdetection;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class ActivityLogFilterHelper {

    public static final String FILTER_DAY = "day";
    public static final String FILTER_DATE = "date";
    public static final String FILTER_WEEK = "week";
    public static final String FILTER_MONTH = "month";

    private ActivityLogFilterHelper() {
    }

    public static List<Map<String, Object>> filter(List<Map<String, Object>> logs,
                                                   String period,
                                                   String dateYmd) {
        return filter(logs, period, dateYmd, null, null);
    }

    public static List<Map<String, Object>> filter(List<Map<String, Object>> logs,
                                                   String period,
                                                   String dateYmd,
                                                   String timeFromHm,
                                                   String timeToHm) {
        if (logs == null || logs.isEmpty()) return new ArrayList<>();
        if (period == null || period.isEmpty()) {
            return applyTimeWindow(new ArrayList<>(logs), timeFromHm, timeToHm);
        }

        long start;
        long end;
        Calendar cal = Calendar.getInstance();

        switch (period) {
            case FILTER_DAY:
                start = startOfDay(cal);
                end = start + 86400000L;
                break;
            case FILTER_DATE:
                if (dateYmd == null || dateYmd.trim().isEmpty()) {
                    return applyTimeWindow(new ArrayList<>(logs), timeFromHm, timeToHm);
                }
                Calendar picked = parseYmd(dateYmd.trim());
                if (picked == null) return new ArrayList<>(logs);
                start = startOfDay(picked);
                end = start + 86400000L;
                break;
            case FILTER_WEEK:
                cal.setFirstDayOfWeek(Calendar.MONDAY);
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                start = startOfDay(cal);
                end = start + 7 * 86400000L;
                break;
            case FILTER_MONTH:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                start = startOfDay(cal);
                cal.add(Calendar.MONTH, 1);
                end = startOfDay(cal);
                break;
            default:
                return applyTimeWindow(new ArrayList<>(logs), timeFromHm, timeToHm);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            long ts = logTimestampMs(log);
            if (ts >= start && ts < end) out.add(log);
        }
        return applyTimeWindow(out, timeFromHm, timeToHm);
    }

    private static List<Map<String, Object>> applyTimeWindow(List<Map<String, Object>> logs,
                                                             String timeFromHm,
                                                             String timeToHm) {
        if ((timeFromHm == null || timeFromHm.trim().isEmpty())
                && (timeToHm == null || timeToHm.trim().isEmpty())) {
            return logs;
        }
        Integer fromMin = parseHmToMinutes(timeFromHm);
        Integer toMin = parseHmToMinutes(timeToHm);
        int from = fromMin != null ? fromMin : 0;
        int to = toMin != null ? toMin : 24 * 60 - 1;

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            long ts = logTimestampMs(log);
            if (ts <= 0) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(ts);
            int minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            if (from <= to) {
                if (minutes >= from && minutes <= to) out.add(log);
            } else if (minutes >= from || minutes <= to) {
                out.add(log);
            }
        }
        return out;
    }

    private static Integer parseHmToMinutes(String hm) {
        if (hm == null || hm.trim().isEmpty()) return null;
        try {
            String[] parts = hm.trim().split(":");
            if (parts.length < 2) return null;
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return h * 60 + m;
        } catch (Exception e) {
            return null;
        }
    }

    public static long logTimestampMs(Map<String, Object> log) {
        if (log == null) return 0;
        Object created = log.get("createdAtMs");
        if (created instanceof Number) return ((Number) created).longValue();
        return toMillis(log.get("timestamp"));
    }

    private static long toMillis(Object ts) {
        if (ts instanceof Timestamp) return ((Timestamp) ts).toDate().getTime();
        if (ts instanceof Date) return ((Date) ts).getTime();
        if (ts instanceof Long) return (Long) ts;
        if (ts instanceof Number) return ((Number) ts).longValue();
        return 0;
    }

    private static long startOfDay(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static Calendar parseYmd(String ymd) {
        try {
            String[] parts = ymd.split("-");
            if (parts.length != 3) return null;
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 1;
            int d = Integer.parseInt(parts[2]);
            Calendar cal = Calendar.getInstance(TimeZone.getDefault(), Locale.US);
            cal.set(y, m, d, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal;
        } catch (Exception e) {
            return null;
        }
    }
}
