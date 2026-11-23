package com.salonplatform.copilot.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The words that belong to one brand's world: its name and its branch and staff names. A question that mentions
 * "Indiranagar" or "Priya" is about this business even if it uses no other business word. Service and product names
 * are deliberately left out: words like "tips" or "classic" appear in them but also in everyday talk, and the shared
 * vocabulary already covers real service words.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantTermsProvider {

    private static final long TTL_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_TERMS = 5000;
    /** Short name parts ("Das", "Ali") are too common in everyday words to prove a question is about this business. */
    private static final int MIN_TERM_LENGTH = 4;
    /** Short joining words in names ("The Hair Studio") that would otherwise make everything look on topic. */
    private static final Set<String> STOP = Set.of("the", "and", "for", "with", "new", "all", "one", "two", "pvt", "ltd", "llp", "inc",
            "men", "women", "kids", "day", "plus", "pro", "max", "best", "top", "special", "regular", "basic", "classic", "premium");

    private record Entry(Set<String> terms, long expires) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public Set<String> termsFor(UUID tenantId) {
        Entry e = cache.get(tenantId);
        long now = System.currentTimeMillis();
        if (e != null && e.expires() > now) return e.terms();
        Set<String> terms = load(tenantId);
        cache.put(tenantId, new Entry(terms, now + TTL_MILLIS));
        return terms;
    }

    private Set<String> load(UUID tenantId) {
        Set<String> terms = new HashSet<>();
        try {
            jdbc.query("""
                    SELECT name FROM branches WHERE tenant_id = :t
                    UNION SELECT name FROM staff WHERE tenant_id = :t
                    UNION SELECT name FROM tenants WHERE id = :t""", Map.of("t", tenantId), rs -> {
                if (terms.size() >= MAX_TERMS) return;
                String name = rs.getString(1);
                if (name == null) return;
                for (String token : name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                    if (token.length() >= MIN_TERM_LENGTH && !STOP.contains(token)) terms.add(token);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Could not load brand vocabulary for the AI gateway: {}", ex.toString());
        }
        return Set.copyOf(terms);
    }
}
