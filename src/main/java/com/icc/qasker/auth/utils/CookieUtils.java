package com.icc.qasker.auth.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;

public class CookieUtils {

    public static ResponseCookie buildCookies(String value) {
        return ResponseCookie.from("refresh_token", value)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(JwtProperties.REFRESH_EXPIRATION_TIME)
            .build();
    }

    public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies()).filter(c -> name.equals(c.getName()))
            .findFirst();
    }

    public static ResponseCookie deleteRefreshCookie() {
        return ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(0) // 삭제
            .build();
    }
}
