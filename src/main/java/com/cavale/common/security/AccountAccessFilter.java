package com.cavale.common.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cavale.user.domain.AccountStatus;
import com.cavale.user.repository.PersonalTokenRepository;
import com.cavale.user.repository.UserAccess;
import com.cavale.user.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces the admin account-access gate on every authenticated request, and
 * lifts the caller's role into the security context.
 *
 * <p>As a plain servlet-filter bean it is auto-registered at the lowest
 * precedence, so it runs <em>after</em> Spring Security's chain (a valid JWT is
 * already authenticated) but before the controller. It then:
 * <ul>
 *   <li>refuses non-admins reaching {@code /api/admin/**} (403);</li>
 *   <li>refuses any non-ACTIVE account (403) — except reading its own profile,
 *       so the SPA can render a "pending activation" screen;</li>
 *   <li>replaces the authentication with one carrying {@code ROLE_<role>}.</li>
 * </ul>
 *
 * <p>Because the status is read from the database on every request, an admin's
 * deactivation takes effect immediately — not only when the 24h token expires.
 *
 * <p>Registered as a {@code @Bean} (see {@code AccountAccessConfig}) rather than
 * component-scanned, so {@code @WebMvcTest} slices — which pull in every
 * {@code Filter} they find — are left untouched.
 */
public class AccountAccessFilter extends OncePerRequestFilter {

    private static final String ADMIN_PREFIX = "/api/admin/";
    private static final String OWN_PROFILE = "/api/users/me";

    private final UserRepository userRepository;
    private final PersonalTokenRepository personalTokenRepository;

    public AccountAccessFilter(UserRepository userRepository,
                               PersonalTokenRepository personalTokenRepository) {
        this.userRepository = userRepository;
        this.personalTokenRepository = personalTokenRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Public endpoints carry no authenticated principal to gate.
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/strava/callback")
                || path.startsWith("/api/strava/webhook")
                // only the health probe is public; any other actuator endpoint
                // still runs through the gate (defence in depth if more are exposed)
                || path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth) || !jwtAuth.isAuthenticated()) {
            // Unauthenticated request to a protected path: let Spring Security's
            // entry point answer with 401 as usual.
            chain.doFilter(request, response);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwtAuth.getToken().getSubject());
        } catch (IllegalArgumentException ex) {
            writeProblem(response, HttpStatus.UNAUTHORIZED, "Invalid token",
                    "Token subject is not a valid account id.");
            return;
        }

        Optional<UserAccess> maybeUser = userRepository.findAccessById(userId);
        if (maybeUser.isEmpty()) {
            writeProblem(response, HttpStatus.UNAUTHORIZED, "Unknown account",
                    "This account no longer exists.");
            return;
        }
        UserAccess user = maybeUser.get();

        // Reject tokens minted before the account's tokens were last revoked.
        // Absent claim (tokens issued before this feature) reads as 0, matching
        // the column default, so existing sessions keep working across rollout.
        Object tvClaim = jwtAuth.getToken().getClaim("tv");
        int tokenVersion = tvClaim instanceof Number n ? n.intValue() : 0;
        if (tokenVersion != user.getTokenVersion()) {
            writeProblem(response, HttpStatus.UNAUTHORIZED, "Session expired",
                    "This credential has been revoked — sign in again.");
            return;
        }

        // Individually revocable PATs: a pat-purpose token carrying a jti must
        // still have an un-revoked bookkeeping row. Legacy PATs (minted before
        // the personal_token table) have no jti and stay honoured until expiry
        // — the account-wide tv bump above remains their only kill switch.
        if ("pat".equals(jwtAuth.getToken().getClaim("purpose"))) {
            String jti = jwtAuth.getToken().getId();
            if (jti != null && !isHonouredPat(jti)) {
                writeProblem(response, HttpStatus.UNAUTHORIZED, "Token revoked",
                        "This personal access token has been revoked.");
                return;
            }
        }

        if (isAdminPath(request) && !user.getRole().name().equals("ADMIN")) {
            writeProblem(response, HttpStatus.FORBIDDEN, "Forbidden",
                    "This action requires administrator privileges.");
            return;
        }

        // A demo sandbox must never mint a long-lived personal access token
        // (MCP credential) — it would outlive the throwaway account.
        if (user.isDemo() && isPatIssue(request)) {
            writeProblem(response, HttpStatus.FORBIDDEN, "Not available in demo",
                    "Personal access tokens can't be created from a demo account.");
            return;
        }

        if (user.getAccountStatus() != AccountStatus.ACTIVE && !isOwnProfileRead(request)) {
            writeProblem(response, HttpStatus.FORBIDDEN, "Account not active",
                    user.getAccountStatus() == AccountStatus.PENDING
                            ? "Your account is awaiting activation by an administrator."
                            : "Your account has been deactivated.");
            return;
        }

        // Lift the role into the context for any downstream authorization.
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        JwtAuthenticationToken authorized = new JwtAuthenticationToken(jwtAuth.getToken(), authorities);
        authorized.setDetails(jwtAuth.getDetails());
        SecurityContextHolder.getContext().setAuthentication(authorized);

        chain.doFilter(request, response);
    }

    private boolean isHonouredPat(String jti) {
        try {
            return personalTokenRepository.existsByJtiAndRevokedAtIsNull(UUID.fromString(jti));
        } catch (IllegalArgumentException ex) {
            return false; // a malformed jti is nobody's credential
        }
    }

    private static boolean isAdminPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ADMIN_PREFIX);
    }

    private static boolean isOwnProfileRead(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && OWN_PROFILE.equals(request.getRequestURI());
    }

    private static boolean isPatIssue(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && (OWN_PROFILE + "/pat").equals(request.getRequestURI());
    }

    /**
     * Writes an RFC 9457 problem-details body by hand. Kept dependency-free (no
     * ObjectMapper) because Boot 4 no longer exposes a plain Jackson 2
     * {@code ObjectMapper} bean; the shape is fixed and the strings are our own
     * constants, so a small writer is simpler and safe.
     */
    private void writeProblem(HttpServletResponse response, HttpStatus status,
                              String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(escape(title), status.value(), escape(detail)));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
