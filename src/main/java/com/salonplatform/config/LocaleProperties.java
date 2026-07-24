package com.salonplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.locales")
public class LocaleProperties {

    private String defaultLocale = "en-IN";

    private List<LocaleEntry> supported = List.of();

    public boolean isEnabled(String code) {
        return supported.stream().anyMatch(e -> e.code().equals(code) && e.enabled());
    }

    public List<LocaleEntry> enabledSorted() {
        return supported.stream()
                .filter(LocaleEntry::enabled)
                .sorted(Comparator.comparingInt(LocaleEntry::sortOrder))
                .toList();
    }

    public record LocaleEntry(
            String code,
            String label,
            String nativeLabel,
            boolean enabled,
            String stateCode,
            String stateName,
            String stateNameNative,
            String regionGroup,
            int sortOrder
    ) {}
}
