package com.salonplatform.forecast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A workbench, not a test: runs the forecast on a CSV of "branch,date,revenue" rows (for example exported from a local or
 * anonymised database) and prints how each model did. Enable with {@code -Dforecast.lab.csv=/path/file.csv}.
 */
class ForecastLabTest {

    @Test
    @EnabledIfSystemProperty(named = "forecast.lab.csv", matches = ".+")
    void printBacktestForCsv() throws Exception {
        Map<String, TreeMap<LocalDate, Double>> byBranch = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(System.getProperty("forecast.lab.csv")))) {
            if (line.isBlank()) continue;
            String[] p = line.split(",");
            byBranch.computeIfAbsent(p[0], k -> new TreeMap<>()).put(LocalDate.parse(p[1]), Double.parseDouble(p[2]));
        }
        LocalDate end = byBranch.values().stream().map(TreeMap::lastKey).max(LocalDate::compareTo).orElseThrow();
        if (System.getProperty("forecast.lab.end") != null) end = LocalDate.parse(System.getProperty("forecast.lab.end"));
        List<Series> series = new ArrayList<>();
        for (var e : byBranch.entrySet()) {
            LocalDate start = e.getValue().firstKey();
            double[] rev = new double[(int) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1];
            for (var d : e.getValue().entrySet()) {
                if (!d.getKey().isAfter(end)) rev[(int) java.time.temporal.ChronoUnit.DAYS.between(start, d.getKey())] = d.getValue();
            }
            series.add(new Series(UUID.randomUUID(), e.getKey(), start, rev));
        }
        double w = Double.parseDouble(System.getProperty("forecast.lab.prior", "0.5"));
        ForecastEngine engine = new ForecastEngine(FestivalCalendar.standard(), w);
        List<LocalDate> origins = Backtest.origins(end);
        List<Trainer> trainers = List.of(new ProfileTrainer(),
                new GamTrainer("trend-and-weekday", 1, false, false, w, FestivalCalendar.standard()),
                new GamTrainer("trend-weekday-festivals", 2, false, true, w, FestivalCalendar.standard()),
                new GamTrainer("trend-weekday-season-festivals", 3, true, true, w, FestivalCalendar.standard()));
        StringBuilder sb = new StringBuilder("\n=== lab, data through " + end + ", prior weight " + w + "\n");
        Map<String, List<List<Backtest.Run>>> brand = new LinkedHashMap<>();
        for (Series s : series) {
            sb.append(s.name()).append('\n');
            for (Trainer t : trainers) {
                List<Backtest.Run> runs = Backtest.run(s, t, origins);
                brand.computeIfAbsent(t.name(), k -> new ArrayList<>()).add(runs);
                Backtest.Score sc = Backtest.score(runs);
                sb.append(String.format("  %-34s origins=%d daily=%.3f week=%.3f month=%.3f bias=%+.3f%n", t.name(), sc.origins(), sc.dailyWape(),
                        sc.week1Error(), sc.month1Error(), sc.bias()));
            }
        }
        sb.append("BRAND (sum of branches)\n");
        for (var e : brand.entrySet()) {
            Backtest.Score sc = Backtest.score(Backtest.sum(e.getValue()));
            sb.append(String.format("  %-34s origins=%d daily=%.3f week=%.3f month=%.3f bias=%+.3f%n", e.getKey(), sc.origins(), sc.dailyWape(),
                    sc.week1Error(), sc.month1Error(), sc.bias()));
        }
        ForecastEngine.Output out = engine.forecast(series, end.plusDays(60));
        sb.append("CHOSEN\n");
        for (ForecastEngine.Result r : out.branches()) {
            sb.append(String.format("  %-18s %-34s conf=%s next7=%.0f next30=%.0f [%.0f..%.0f]%n", r.name(), r.quality().model(), r.quality().confidence(),
                    r.totals().next7(), r.totals().next30(), r.totals().next30Low(), r.totals().next30High()));
        }
        sb.append(String.format("  BRAND next30=%.0f [%.0f..%.0f] conf=%s month-err=%.3f%n", out.brand().totals().next30(), out.brand().totals().next30Low(),
                out.brand().totals().next30High(), out.brand().quality().confidence(), out.brand().quality().monthError()));
        out.festivals().forEach(f -> sb.append(String.format("  festival %-18s %s impact=%.0f (%.1f%%) learned=%s seen=%d%n", f.name(), f.date(), f.impact(),
                f.percent() * 100, f.learned(), f.timesSeen())));
        System.out.println(sb);
        Files.writeString(Path.of(System.getProperty("forecast.lab.out", "/tmp/forecast-lab.txt")), sb.toString());
    }
}
