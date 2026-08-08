package com.cavale.user.web;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.cavale.common.security.JwtProperties;
import com.cavale.user.service.RefreshTokenService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Carries the refresh token in an HttpOnly cookie rather than in the response
 * body.
 *
 * <p>The access token has to live in JavaScript's reach — it goes in an
 * Authorization header on every call — so cross-site scripting could always
 * lift a day of access. The refresh token is what turns that into permanent
 * access, so it is the one credential kept where scripts cannot read it.
 *
 * <p>{@code SameSite=Lax} is what makes the refresh endpoint safe without a
 * CSRF token: browsers will not attach the cookie to a cross-site POST, so
 * another origin cannot spend the athlete's refresh token. {@code Path} keeps
 * the cookie off every other request.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "cavale_refresh";
    private static final String PATH = "/api/auth";

    private final JwtProperties jwtProperties;

    public RefreshCookie(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public void set(HttpServletResponse response, RefreshTokenService.IssuedRefresh refresh) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), refresh.expiresAt()).getSeconds());
        response.addHeader(HttpHeaders.SET_COOKIE, base(refresh.secret())
                .maxAge(maxAge)
                .build()
                .toString());
    }

    /** Expire it in place — the browser drops it on receipt. */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, base("").maxAge(0).build().toString());
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(jwtProperties.secureCookie())
                .sameSite("Lax")
                .path(PATH);
    }
}
