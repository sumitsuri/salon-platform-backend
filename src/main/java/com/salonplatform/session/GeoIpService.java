package com.salonplatform.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Best-effort "where is this IP" lookup, shown to people in their Signed-in devices list. It never blocks or fails
 * a sign-in: lookups run off the request thread, time out quickly and degrade to "no location".
 *
 * <p>Privacy: with the default provider the IP address is sent to a third-party geolocation service. Turn this off
 * with {@code app.auth.geo.enabled=false}, or point {@code app.auth.geo.url} at a self-hosted service.
 */
@Service
@Slf4j
public class GeoIpService {

    static final String LOCAL_NETWORK = "Local network";

    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{3,45}$");
    private static final Duration HIT_TTL = Duration.ofHours(24);
    private static final Duration MISS_TTL = Duration.ofMinutes(10);
    private static final int MAX_CACHE = 2000;

    private final boolean enabled;
    private final String urlTemplate;
    private final RestTemplate http;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE;
        }
    };

    private record CacheEntry(String location, Instant expiresAt) {}

    public GeoIpService(
            @Value("${app.auth.geo.enabled:true}") boolean enabled,
            @Value("${app.auth.geo.url:https://ipwho.is/{ip}?fields=success,city,region,country}") String urlTemplate,
            @Value("${app.auth.geo.timeout-ms:2000}") int timeoutMs) {
        this.enabled = enabled;
        this.urlTemplate = urlTemplate;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.http = new RestTemplate(factory);
    }

    /** Returns e.g. "Bengaluru, Karnataka, India", "Local network", or empty when unknown/disabled. */
    public Optional<String> locate(String ip) {
        if (ip == null || !IP_LITERAL.matcher(ip).matches()) {
            return Optional.empty();
        }
        if (isPrivate(ip)) {
            return Optional.of(LOCAL_NETWORK);
        }
        if (!enabled) {
            return Optional.empty();
        }
        synchronized (cache) {
            CacheEntry hit = cache.get(ip);
            if (hit != null && hit.expiresAt().isAfter(Instant.now())) {
                return Optional.ofNullable(hit.location());
            }
        }
        String location = lookupRemote(ip);
        synchronized (cache) {
            cache.put(ip, new CacheEntry(location, Instant.now().plus(location != null ? HIT_TTL : MISS_TTL)));
        }
        return Optional.ofNullable(location);
    }

    @SuppressWarnings("unchecked")
    private String lookupRemote(String ip) {
        try {
            Map<String, Object> body = http.getForObject(urlTemplate, Map.class, ip);
            if (body == null || Boolean.FALSE.equals(body.get("success"))) {
                return null;
            }
            return formatLocation(body.get("city"), body.get("region"), body.get("country"));
        } catch (Exception e) {
            log.debug("Geo lookup failed for {}: {}", ip, e.getMessage());
            return null;
        }
    }

    static String formatLocation(Object city, Object region, Object country) {
        String joined = Stream.of(city, region, country)
                .filter(v -> v != null && !v.toString().isBlank())
                .map(Object::toString)
                .distinct()
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }

    static boolean isPrivate(String ip) {
        String lower = ip.toLowerCase();
        if (lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80") || lower.equals("::1")) {
            return true;
        }
        if (!ip.contains(".") && !ip.contains(":")) {
            return false;
        }
        try {
            // Literal addresses only (guarded by IP_LITERAL), so this never triggers a DNS lookup.
            InetAddress a = InetAddress.getByName(ip);
            return a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
