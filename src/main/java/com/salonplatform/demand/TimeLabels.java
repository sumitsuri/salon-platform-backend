package com.salonplatform.demand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/** Plain-English hours and days ("5-8 pm", "Tue-Thu", "weekends") for recommendation text. */
public final class TimeLabels {

    private static final String[] DAY_SHORT = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String[] DAY_LONG = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private TimeLabels() {}

    public static String dayShort(int isoDow) {
        return DAY_SHORT[isoDow - 1];
    }

    public static String dayLong(int isoDow) {
        return DAY_LONG[isoDow - 1];
    }

    /** 0 → "12 am", 12 → "12 pm", 17 → "5 pm". */
    public static String hour(int h) {
        int hh = ((h % 24) + 24) % 24;
        int twelve = hh % 12 == 0 ? 12 : hh % 12;
        return twelve + " " + (hh < 12 ? "am" : "pm");
    }

    /** [start, end) as "5-8 pm", or "11 am-2 pm" when it crosses noon. */
    public static String range(int start, int end) {
        String s = hour(start);
        String e = hour(end);
        String sMeridiem = s.substring(s.length() - 2);
        String eMeridiem = e.substring(e.length() - 2);
        if (sMeridiem.equals(eMeridiem)) {
            return s.substring(0, s.length() - 3) + "-" + e;
        }
        return s + "-" + e;
    }

    /** {1..7} → "every day"; {6,7} → "weekends"; {1..5} → "weekdays"; runs of 3+ → "Tue-Thu"; otherwise "Mon, Wed". */
    public static String days(Collection<Integer> isoDays) {
        TreeSet<Integer> set = new TreeSet<>(isoDays);
        if (set.isEmpty()) return "";
        if (set.size() == 7) return "every day";
        if (set.equals(new TreeSet<>(List.of(6, 7)))) return "weekends";
        if (set.equals(new TreeSet<>(List.of(1, 2, 3, 4, 5)))) return "weekdays";
        if (set.size() == 1) return dayLong(set.first()) + "s";
        List<String> parts = new ArrayList<>();
        int runStart = -1;
        int prev = -1;
        for (int d : set) {
            if (runStart == -1) {
                runStart = d;
            } else if (d != prev + 1) {
                parts.add(runLabel(runStart, prev));
                runStart = d;
            }
            prev = d;
        }
        parts.add(runLabel(runStart, prev));
        return String.join(", ", parts);
    }

    private static String runLabel(int from, int to) {
        if (from == to) return dayShort(from);
        if (to == from + 1) return dayShort(from) + ", " + dayShort(to);
        return dayShort(from) + "-" + dayShort(to);
    }
}
