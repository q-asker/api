package com.icc.qasker.auth.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.icc.qasker.auth.service.TokenRotationService;
import com.icc.qasker.auth.utils.CookieUtils;
import com.icc.qasker.auth.utils.CustomHttpServletRequest;
import com.icc.qasker.auth.utils.JwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class RefreshRotationFilter extends OncePerRequestFilter {

    private final TokenRotationService tokenRotationService;
    private static final String BEARER_PREFIX = "Bearer ";

    private boolean skip(String path) {
        // 인증이 필요하지 않은 url 적기
        return path.startsWith("/auth/")
            || path.startsWith("/oauth2")
            || path.startsWith("/login/oauth2")
            || path.startsWith("/s3/upload")
            || path.startsWith("/explanation/")
            || path.startsWith("/problem-set/")
            || path.startsWith("/specific-explanation/")
            || path.startsWith("/generation");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        if (skip(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith(BEARER_PREFIX)) {
            String at = auth.substring(BEARER_PREFIX.length());
            try {
                JWT.require(Algorithm.HMAC512(JwtProperties.SECRET)).build().verify(at);
                filterChain.doFilter(request, response);
                return;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        var rtCookie = CookieUtils.getCookie(request, "refresh_token").orElse(null);
        if (rtCookie == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String newAt = tokenRotationService.rotateTokens(rtCookie.getValue(), response);
            CustomHttpServletRequest customRequest = new CustomHttpServletRequest(request);
            customRequest.putHeader(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + newAt);
            filterChain.doFilter(customRequest, response);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
        }
    }
}
