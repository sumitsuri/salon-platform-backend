package com.salonplatform.offerposter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salonplatform.domain.entity.FestiveOffer;
import com.salonplatform.dto.offerposter.OfferBlockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Converts between the stored offer (JSON columns) and the objects the builders work with. */
@Component
@RequiredArgsConstructor
@Slf4j
public class FestiveOfferMapper {

    private final ObjectMapper mapper;

    public String writeBlocks(List<OfferBlockDto> blocks) {
        return write(blocks);
    }

    public String writeStrings(List<String> values) {
        return write(values == null ? List.of() : values);
    }

    public List<OfferBlockDto> readBlocks(String json) {
        try {
            if (json == null || json.isBlank()) return new ArrayList<>();
            return mapper.readValue(json, new TypeReference<List<OfferBlockDto>>() {});
        } catch (Exception e) {
            log.warn("Could not read offer blocks: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<String> readStrings(String json) {
        try {
            if (json == null || json.isBlank()) return new ArrayList<>();
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public OfferContent toContent(FestiveOffer o) {
        return new OfferContent(
                enumOrDefault(PosterOccasion.class, o.getOccasion(), PosterOccasion.GENERAL),
                enumOrDefault(PosterStyle.class, o.getStyle(), PosterStyle.FESTIVE_MAROON_GOLD),
                enumOrDefault(PosterFormat.class, o.getFormat(), PosterFormat.PORTRAIT),
                o.getBrandName(),
                o.getHeadline(),
                o.getTagline(),
                o.getAddress(),
                o.getPhone(),
                o.getValidUntil(),
                o.getTerms(),
                readBlocks(o.getBlocksJson()),
                readStrings(o.getIncludedServicesJson()),
                o.getExtraDirection());
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not store offer content", e);
        }
    }

    public static <E extends Enum<E>> E enumOrDefault(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
