package com.salonplatform.telemetry;

import com.salonplatform.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records successful product API calls as usage. Reads are collapsed to one event per user and feature
 * every 10 minutes so dashboard polling does not flood the stream. Writes collapse to one event per 15
 * seconds so a single bill (lines, discount, payment) still counts as billing without one row per keystroke.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductActivityFilter extends OncePerRequestFilter {

    static final long READ_WINDOW_MS = 10 * 60 * 1000L;
    static final long WRITE_WINDOW_MS = 15 * 1000L;

    private final ProductTelemetryIngestService ingestService;
    private final ConcurrentHashMap<String, Long> recent = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);
        try {
            record(request, response);
        } catch (Exception e) {
            log.debug("Product activity not recorded: {}", e.getMessage());
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response) {
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal user)) {
            return;
        }
        String method = request.getMethod();
        if (method == null || "OPTIONS".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return;
        }
        ProductActivityCatalog.classify(user.getRole(), method, request.getRequestURI())
                .filter(classified -> admit(
                        user.getId() + "|" + classified.featureKey(), ProductActivityCatalog.isMutation(method)))
                .ifPresent(classified -> ingestService.recordActivity(
                        user,
                        classified.portal(),
                        classified.featureKey(),
                        ProductActivityCatalog.normalize(request.getRequestURI()),
                        method.toUpperCase(Locale.ROOT)));
    }

    private boolean admit(String key, boolean mutation) {
        long now = System.currentTimeMillis();
        long window = mutation ? WRITE_WINDOW_MS : READ_WINDOW_MS;
        // Reads and writes debounce separately so a bill-preview poll cannot hide the payment.
        String bucket = key + (mutation ? "|w" : "|r");
        AtomicBoolean allowed = new AtomicBoolean(false);
        recent.compute(bucket, (k, previous) -> {
            if (previous == null || now - previous >= window) {
                allowed.set(true);
                return now;
            }
            return previous;
        });
        if (recent.size() > 5_000) {
            evictStale(now);
        }
        return allowed.get();
    }

    private void evictStale(long now) {
        Iterator<Map.Entry<String, Long>> it = recent.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > READ_WINDOW_MS) {
                it.remove();
            }
        }
    }
}
