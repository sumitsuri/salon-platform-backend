package com.salonplatform.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CampaignFilterValues {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CampaignFilterValues() {}

    public static String serialize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> cleaned = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.size() == 1) {
            return cleaned.get(0);
        }
        try {
            return MAPPER.writeValueAsString(cleaned);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize campaign filter values", e);
        }
    }

    public static List<String> deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (raw.startsWith("[")) {
            try {
                return MAPPER.readValue(raw, new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                return List.of(raw.trim());
            }
        }
        return List.of(raw.trim());
    }

    public static String serializeUuids(List<UUID> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> cleaned = values.stream()
                .filter(v -> v != null)
                .map(UUID::toString)
                .distinct()
                .collect(Collectors.toList());
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.size() == 1) {
            return cleaned.get(0);
        }
        try {
            return MAPPER.writeValueAsString(cleaned);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize campaign filter UUIDs", e);
        }
    }

    public static List<UUID> deserializeUuids(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (raw.startsWith("[")) {
            try {
                List<String> strings = MAPPER.readValue(raw, new TypeReference<List<String>>() {});
                return strings.stream().map(UUID::fromString).toList();
            } catch (JsonProcessingException e) {
                return List.of(UUID.fromString(raw.trim()));
            }
        }
        return List.of(UUID.fromString(raw.trim()));
    }

    public static List<String> resolveNames(String single, List<String> multi) {
        if (multi != null && !multi.isEmpty()) {
            return multi.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }
        if (single != null && !single.isBlank()) {
            return List.of(single.trim());
        }
        return List.of();
    }
}
