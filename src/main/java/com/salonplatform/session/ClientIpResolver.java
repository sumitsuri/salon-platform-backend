package com.salonplatform.session;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Works out the caller's IP. In production the request reaches the API through an ALB and then nginx, and each of
 * them appends one entry to {@code X-Forwarded-For}. The real client is therefore the entry {@code hops} places from
 * the right; anything further left was supplied by the caller and cannot be trusted.
 */
@Component
public class ClientIpResolver {

    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{3,45}$");

    private final int trustedProxyHops;

    public ClientIpResolver(@Value("${app.auth.trusted-proxy-hops:0}") int trustedProxyHops) {
        this.trustedProxyHops = Math.max(0, trustedProxyHops);
    }

    public ClientInfo resolve(HttpServletRequest request, String portal) {
        return new ClientInfo(resolveIp(request), truncate(request.getHeader("User-Agent"), 500), normalisePortal(portal));
    }

    String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = pickForwarded(forwarded, trustedProxyHops);
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        return ip != null && IP_LITERAL.matcher(ip).matches() ? ip : null;
    }

    static String pickForwarded(String header, int hops) {
        if (hops <= 0 || !StringUtils.hasText(header)) {
            return null;
        }
        String[] parts = header.split(",");
        int index = Math.max(0, parts.length - hops);
        String candidate = parts[index].trim();
        return candidate.isEmpty() ? null : candidate;
    }

    private static String normalisePortal(String portal) {
        if (!StringUtils.hasText(portal)) return null;
        String p = portal.trim().toLowerCase();
        return p.length() > 20 ? null : p;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
