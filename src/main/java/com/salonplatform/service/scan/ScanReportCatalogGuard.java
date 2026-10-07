package com.salonplatform.service.scan;

import com.salonplatform.dto.facescan.*;
import com.salonplatform.dto.scalpscan.*;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Drops LLM/heuristic picks that are not in the filtered branch catalog. */
public final class ScanReportCatalogGuard {

    private ScanReportCatalogGuard() {}

    public static void enforceScalp(ScalpScanReportDto report, Set<String> allowedNamesLower) {
        if (report == null || allowedNamesLower.isEmpty()) {
            return;
        }
        if (report.getInSalonServices() != null) {
            report.setInSalonServices(report.getInSalonServices().stream()
                    .filter(s -> allowedNamesLower.contains(s.getName().toLowerCase(Locale.ROOT)))
                    .toList());
        }
    }

    public static void enforceFace(FaceScanReportDto report, Set<String> allowedNamesLower) {
        if (report == null || allowedNamesLower.isEmpty()) {
            return;
        }
        if (report.getInSalonServices() != null) {
            report.setInSalonServices(report.getInSalonServices().stream()
                    .filter(s -> allowedNamesLower.contains(s.getName().toLowerCase(Locale.ROOT)))
                    .toList());
        }
        if (report.getCarePlanPhases() != null) {
            report.setCarePlanPhases(report.getCarePlanPhases().stream()
                    .map(phase -> {
                        FaceScanServiceSuggestionDto visit = phase.getInSalonVisit();
                        if (visit != null
                                && !allowedNamesLower.contains(visit.getName().toLowerCase(Locale.ROOT))) {
                            return FaceScanCarePlanPhaseDto.builder()
                                    .month(phase.getMonth())
                                    .title(phase.getTitle())
                                    .goal(phase.getGoal())
                                    .rationale(phase.getRationale())
                                    .visitCadence(phase.getVisitCadence())
                                    .inSalonVisit(null)
                                    .homeRoutine(phase.getHomeRoutine())
                                    .build();
                        }
                        return phase;
                    })
                    .toList());
        }
    }

    public static Set<String> namesLower(ScanBranchCatalogService.CatalogEntry... entries) {
        return Set.of(entries).stream()
                .map(e -> e.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public static Set<String> namesLowerFromStrings(java.util.List<String> names) {
        return names.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
