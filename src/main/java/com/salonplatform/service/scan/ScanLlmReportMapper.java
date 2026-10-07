package com.salonplatform.service.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.salonplatform.dto.facescan.*;
import com.salonplatform.dto.scalpscan.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ScanLlmReportMapper {

    private ScanLlmReportMapper() {}

    public static void mergeScalp(ScalpScanReportDto report, JsonNode llm) {
        if (llm == null) return;
        List<ScalpScanConcernDto> concerns = mapScalpConcerns(llm.path("concerns"));
        if (!concerns.isEmpty()) {
            report.setConcerns(concerns);
        }
        List<ScalpScanServiceSuggestionDto> services = mapScalpServices(llm.path("inSalonServices"));
        if (!services.isEmpty()) {
            report.setInSalonServices(services);
        }
        List<ScalpScanRoutineStepDto> steps = mapScalpRoutine(llm.path("routineSteps"));
        if (!steps.isEmpty()) {
            report.setRoutineSteps(steps);
        }
    }

    public static void mergeFace(FaceScanReportDto report, JsonNode llm) {
        if (llm == null) return;
        List<FaceScanConcernDto> concerns = mapFaceConcerns(llm.path("concerns"));
        if (!concerns.isEmpty()) {
            report.setConcerns(concerns);
        }
        List<FaceScanCarePlanPhaseDto> phases = mapFacePhases(llm.path("carePlanPhases"));
        if (!phases.isEmpty()) {
            report.setCarePlanPhases(phases);
            List<FaceScanServiceSuggestionDto> flat = phases.stream()
                    .map(FaceScanCarePlanPhaseDto::getInSalonVisit)
                    .filter(v -> v != null)
                    .toList();
            report.setInSalonServices(flat);
            if (!phases.isEmpty() && phases.get(phases.size() - 1).getHomeRoutine() != null) {
                report.setRoutineSteps(phases.get(phases.size() - 1).getHomeRoutine());
            }
        } else {
            List<FaceScanServiceSuggestionDto> services = mapFaceServices(llm.path("inSalonServices"));
            if (!services.isEmpty()) {
                report.setInSalonServices(services);
            }
        }
        List<FaceScanRoutineStepDto> steps = mapFaceRoutine(llm.path("routineSteps"));
        if (!steps.isEmpty() && (report.getCarePlanPhases() == null || report.getCarePlanPhases().isEmpty())) {
            report.setRoutineSteps(steps);
        }
    }

    private static List<ScalpScanConcernDto> mapScalpConcerns(JsonNode arr) {
        List<ScalpScanConcernDto> list = new ArrayList<>();
        if (!arr.isArray()) return list;
        arr.forEach(n -> list.add(ScalpScanConcernDto.builder()
                .code(n.path("code").asText("BALANCED"))
                .label(n.path("label").asText("Concern"))
                .severity(n.path("severity").asText("LOW"))
                .score(60)
                .insight(n.path("insight").asText(null))
                .build()));
        return list.stream().limit(3).toList();
    }

    private static List<FaceScanConcernDto> mapFaceConcerns(JsonNode arr) {
        List<ScalpScanConcernDto> tmp = mapScalpConcerns(arr);
        return tmp.stream()
                .map(c -> FaceScanConcernDto.builder()
                        .code(c.getCode())
                        .label(c.getLabel())
                        .severity(c.getSeverity())
                        .score(c.getScore())
                        .insight(c.getInsight())
                        .build())
                .toList();
    }

    private static List<ScalpScanServiceSuggestionDto> mapScalpServices(JsonNode arr) {
        List<ScalpScanServiceSuggestionDto> list = new ArrayList<>();
        if (!arr.isArray()) return list;
        arr.forEach(n -> list.add(ScalpScanServiceSuggestionDto.builder()
                .name(n.path("name").asText())
                .reason(n.path("reason").asText())
                .branchServiceId(parseUuid(n.path("branchServiceId").asText(null)))
                .build()));
        return list.stream().limit(2).toList();
    }

    private static List<FaceScanServiceSuggestionDto> mapFaceServices(JsonNode arr) {
        return mapScalpServices(arr).stream()
                .map(s -> FaceScanServiceSuggestionDto.builder()
                        .branchServiceId(s.getBranchServiceId())
                        .name(s.getName())
                        .reason(s.getReason())
                        .build())
                .toList();
    }

    private static List<ScalpScanRoutineStepDto> mapScalpRoutine(JsonNode arr) {
        List<ScalpScanRoutineStepDto> list = new ArrayList<>();
        if (!arr.isArray()) return list;
        arr.forEach(n -> list.add(ScalpScanRoutineStepDto.builder()
                .step(n.path("step").asInt(list.size() + 1))
                .phase(n.path("phase").asText())
                .title(n.path("title").asText())
                .description(n.path("description").asText())
                .build()));
        return list.stream().limit(5).toList();
    }

    private static List<FaceScanRoutineStepDto> mapFaceRoutine(JsonNode arr) {
        return mapScalpRoutine(arr).stream()
                .map(s -> FaceScanRoutineStepDto.builder()
                        .step(s.getStep())
                        .phase(s.getPhase())
                        .title(s.getTitle())
                        .description(s.getDescription())
                        .build())
                .toList();
    }

    private static List<FaceScanCarePlanPhaseDto> mapFacePhases(JsonNode arr) {
        List<FaceScanCarePlanPhaseDto> list = new ArrayList<>();
        if (!arr.isArray()) return list;
        arr.forEach(n -> {
            JsonNode visit = n.path("inSalonVisit");
            FaceScanServiceSuggestionDto inSalon = visit.isMissingNode() ? null : FaceScanServiceSuggestionDto.builder()
                    .name(visit.path("name").asText())
                    .reason(visit.path("reason").asText())
                    .branchServiceId(parseUuid(visit.path("branchServiceId").asText(null)))
                    .build();
            list.add(FaceScanCarePlanPhaseDto.builder()
                    .month(n.path("month").asInt(list.size() + 1))
                    .title(n.path("title").asText())
                    .goal(n.path("goal").asText())
                    .rationale(n.path("rationale").asText())
                    .visitCadence("1 in-salon visit this month + daily home care")
                    .inSalonVisit(inSalon)
                    .homeRoutine(mapFaceRoutine(n.path("homeRoutine")))
                    .build());
        });
        return list.stream().limit(3).toList();
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
