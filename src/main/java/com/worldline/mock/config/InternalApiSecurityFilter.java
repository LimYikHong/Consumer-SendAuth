package com.worldline.mock.config;

import com.worldline.mock.service.KeyExchangeAuditService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Security filter for the internal API endpoints (/api/internal/**). Validates:
 * 1. API Key — must match the configured key via X-API-Key header 2. IP
 * Whitelist — request must come from an allowed IP address
 *
 * Dashboard pages (/, /batch/**) are NOT filtered — no auth needed for UI.
 */
@Component
@Order(1)
@Slf4j
public class InternalApiSecurityFilter implements Filter {

    @Value("${app.security.api-key}")
    private String apiKey;

    @Value("${app.security.ip-whitelist}")
    private String ipWhitelistStr;

    @Autowired
    private KeyExchangeAuditService auditService;

    private List<String> getIpWhitelist() {
        return Arrays.asList(ipWhitelistStr.split(","));
    }

    private static final String API_KEY_HEADER = "X-API-Key";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;
        String path = httpReq.getRequestURI();

        // Only protect /api/internal/** endpoints
        if (!path.startsWith("/api/internal")) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Check API Key
        String providedKey = httpReq.getHeader(API_KEY_HEADER);
        if (providedKey == null || !providedKey.equals(apiKey)) {
            log.warn("🚫 Rejected request to {} — invalid API key from IP {}",
                    path, getClientIp(httpReq));
            auditService.logFailure(getClientIp(httpReq), path, "UNAUTHORIZED",
                    "Invalid or missing API key", httpReq.getHeader("User-Agent"));
            httpRes.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpRes.setContentType("application/json");
            httpRes.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
            return;
        }

        // 2. Check IP whitelist
        String clientIp = getClientIp(httpReq);
        if (!isIpAllowed(clientIp)) {
            log.warn("🚫 Rejected request to {} — IP {} not in whitelist", path, clientIp);
            auditService.logFailure(clientIp, path, "FORBIDDEN",
                    "IP address not in whitelist", httpReq.getHeader("User-Agent"));
            httpRes.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpRes.setContentType("application/json");
            httpRes.getWriter().write("{\"error\":\"IP address not allowed\"}");
            return;
        }

        log.debug("✅ Internal API access granted for {} from IP {}", path, clientIp);
        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        // Check common proxy headers first
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isIpAllowed(String clientIp) {
        List<String> whitelist = getIpWhitelist();
        // Normalize IPv6 loopback
        String normalized = clientIp;
        if ("0:0:0:0:0:0:0:1".equals(clientIp) || "::1".equals(clientIp)) {
            normalized = "127.0.0.1";
        }
        return whitelist.contains(normalized) || whitelist.contains(clientIp);
    }
}
