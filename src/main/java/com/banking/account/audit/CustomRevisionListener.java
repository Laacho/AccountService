package com.banking.account.audit;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Hibernate Envers {@link RevisionListener} that populates the custom fields
 * of {@link CustomRevisionEntity} before it is persisted.
 *
 * <p>Envers calls {@link #newRevision(Object)} synchronously within the same
 * transaction as the audited entity change, so both the revision row and the
 * {@code *_aud} shadow rows are committed atomically.</p>
 *
 * <p><strong>How userId is obtained:</strong> The API Gateway extracts the
 * userId from the validated JWT and injects it as the Spring Security
 * {@code Authentication#getPrincipal()}. This service's security config
 * (JwtAuthenticationFilter) parses the {@code X-User-Id} header set by the
 * gateway and populates the SecurityContext accordingly.</p>
 *
 * <p><strong>Background jobs:</strong> When the @Scheduled transaction processor
 * runs, there is no HTTP request and no SecurityContext. In that case
 * {@code modifiedBy} will be set to "SYSTEM" and {@code clientIp} to null.</p>
 */
@Slf4j
public class CustomRevisionListener implements RevisionListener {

    private static final String SYSTEM_PRINCIPAL = "SYSTEM";

    @Override
    public void newRevision(Object revisionEntity) {
        CustomRevisionEntity revision = (CustomRevisionEntity) revisionEntity;

        // ── 1. Resolve the acting user ──────────────────────────────────
        try {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof String userId) {
                revision.setModifiedBy(userId);
            } else {
                revision.setModifiedBy(SYSTEM_PRINCIPAL);
            }
        } catch (Exception e) {
            log.warn("Could not resolve authenticated user for revision audit: {}", e.getMessage());
            revision.setModifiedBy(SYSTEM_PRINCIPAL);
        }

        // ── 2. Resolve the client IP ─────────────────────────────────────
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attrs != null) {
                String ip = resolveClientIp(attrs);
                revision.setClientIp(ip);
            }
        } catch (Exception e) {
            log.debug("Could not resolve client IP for revision audit: {}", e.getMessage());
            // Not critical — leave null for background jobs
        }
    }

    /**
     * Attempts to extract the real client IP, respecting common reverse-proxy headers.
     * The API Gateway adds an {@code X-Forwarded-For} or {@code X-Real-IP} header
     * when it forwards requests to internal services.
     */
    private String resolveClientIp(ServletRequestAttributes attrs) {
        var request = attrs.getRequest();

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; take the first
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
