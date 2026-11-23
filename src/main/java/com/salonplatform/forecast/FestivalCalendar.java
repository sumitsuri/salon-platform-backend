package com.salonplatform.forecast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indian festivals and shopping occasions that move salon sales, with the dates for 2024 to 2027.
 *
 * <p>Each festival has up to three phases around its main day: the run-up (people book grooming before it), the day
 * itself (some days boom, some are quiet because everyone is celebrating), and the days after. The {@code prior}
 * values are starting assumptions about how much each phase moves sales, as a change in the logarithm of revenue
 * (0.10 is about +10%). They are deliberately modest and are scaled down further by the model; a brand's own history
 * replaces them as soon as it has seen the festival. Festivals with no prior are learned from the brand's data only.
 */
public final class FestivalCalendar {

    public enum Phase { RUN_UP, DAY, AFTER }

    /** One festival: how long its run-up and aftermath last, and the starting assumption for each phase. */
    public record Festival(String id, String name, int runUpDays, int afterDays, double priorRunUp, double priorDay, double priorAfter) {
        public int window(Phase p) {
            return switch (p) { case RUN_UP -> runUpDays; case DAY -> 1; case AFTER -> afterDays; };
        }
        public double prior(Phase p) {
            return switch (p) { case RUN_UP -> priorRunUp; case DAY -> priorDay; case AFTER -> priorAfter; };
        }
    }

    /** A festival phase that falls on a given date. */
    public record Hit(Festival festival, Phase phase, LocalDate mainDay) {
        public String key() {
            return festival.id() + ":" + phase.name();
        }
    }

    private static final List<Festival> FESTIVALS = List.of(
            new Festival("DIWALI", "Diwali", 7, 2, 0.18, -0.35, -0.12),
            new Festival("KARVA_CHAUTH", "Karva Chauth", 2, 0, 0.10, 0.25, 0),
            new Festival("RAKSHA_BANDHAN", "Raksha Bandhan", 3, 0, 0.08, 0.05, 0),
            new Festival("BHAI_DOOJ", "Bhai Dooj", 0, 0, 0, -0.05, 0),
            new Festival("HOLI", "Holi", 3, 1, 0.05, -0.30, -0.05),
            new Festival("NAVRATRI", "Navratri", 0, 0, 0, 0, 0),
            new Festival("DUSSEHRA", "Dussehra", 3, 0, 0.06, -0.10, 0),
            new Festival("GANESH_CHATURTHI", "Ganesh Chaturthi", 0, 0, 0, -0.08, 0),
            new Festival("EID_AL_FITR", "Eid al-Fitr", 4, 0, 0.12, -0.15, 0),
            new Festival("EID_AL_ADHA", "Eid al-Adha", 2, 0, 0.05, -0.10, 0),
            new Festival("ONAM", "Onam", 3, 0, 0, 0, 0),
            new Festival("UGADI", "Ugadi / Gudi Padwa", 2, 0, 0, 0, 0),
            new Festival("AKSHAYA_TRITIYA", "Akshaya Tritiya", 0, 0, 0, 0, 0),
            new Festival("JANMASHTAMI", "Janmashtami", 0, 0, 0, 0, 0),
            new Festival("MOTHERS_DAY", "Mother's Day", 3, 0, 0.05, 0.08, 0),
            new Festival("VALENTINES_DAY", "Valentine's Day", 2, 0, 0.08, 0.10, 0),
            new Festival("WOMENS_DAY", "Women's Day", 0, 0, 0, 0.10, 0),
            new Festival("MAKAR_SANKRANTI", "Makar Sankranti / Pongal", 0, 0, 0, 0, 0),
            new Festival("REPUBLIC_DAY", "Republic Day", 0, 0, 0, -0.05, 0),
            new Festival("INDEPENDENCE_DAY", "Independence Day", 0, 0, 0, -0.05, 0),
            new Festival("CHRISTMAS", "Christmas", 3, 0, 0.10, -0.05, 0),
            new Festival("NEW_YEARS_EVE", "New Year's Eve", 2, 1, 0.12, 0.10, -0.10));

    private final Map<String, Festival> byId = new LinkedHashMap<>();
    private final Map<LocalDate, List<Hit>> hitsByDate = new java.util.HashMap<>();
    private final List<Occurrence> occurrences = new ArrayList<>();
    private LocalDate coverageEnd = LocalDate.MIN;

    /** A festival's main day in a given year. */
    public record Occurrence(Festival festival, LocalDate date) {}

    private FestivalCalendar() {
        for (Festival f : FESTIVALS) byId.put(f.id(), f);
    }

    private static final FestivalCalendar INSTANCE = load();

    public static FestivalCalendar standard() {
        return INSTANCE;
    }

    private static FestivalCalendar load() {
        FestivalCalendar cal = new FestivalCalendar();
        try (InputStream in = FestivalCalendar.class.getResourceAsStream("/forecast/festival-dates.csv")) {
            if (in == null) throw new IllegalStateException("festival-dates.csv is missing");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] p = line.split(",");
                    Festival f = cal.byId.get(p[0].trim());
                    if (f == null) throw new IllegalStateException("Unknown festival " + p[0]);
                    cal.add(f, LocalDate.parse(p[1].trim()));
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read the festival calendar", e);
        }
        cal.occurrences.sort(Comparator.comparing(Occurrence::date));
        return cal;
    }

    private void add(Festival f, LocalDate day) {
        occurrences.add(new Occurrence(f, day));
        if (day.isAfter(coverageEnd)) coverageEnd = day;
        for (Phase p : Phase.values()) {
            int n = f.window(p);
            for (int k = 0; k < n; k++) {
                LocalDate d = switch (p) {
                    case RUN_UP -> day.minusDays(k + 1L);
                    case DAY -> day;
                    case AFTER -> day.plusDays(k + 1L);
                };
                hitsByDate.computeIfAbsent(d, x -> new ArrayList<>()).add(new Hit(f, p, day));
            }
        }
    }

    public List<Festival> festivals() {
        return List.copyOf(byId.values());
    }

    public Festival festival(String id) {
        return byId.get(id);
    }

    /** Phases of any festival that fall on {@code date}. */
    public List<Hit> on(LocalDate date) {
        return hitsByDate.getOrDefault(date, List.of());
    }

    /** Festival main days in [from, to], oldest first. */
    public List<Occurrence> between(LocalDate from, LocalDate to) {
        return occurrences.stream().filter(o -> !o.date().isBefore(from) && !o.date().isAfter(to)).toList();
    }

    /** The last date the calendar knows; forecasts beyond it have no festival information. */
    public LocalDate coverageEnd() {
        return coverageEnd;
    }
}
