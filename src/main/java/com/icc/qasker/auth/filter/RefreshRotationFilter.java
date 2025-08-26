package com.icc.qasker.auth.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
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

    private boolean skip(String path) {
        // 인증이 필요하지 않은 url 적기
        return path.startsWith("/auth/login")
            || path.startsWith("/auth/join")
            || path.startsWith("/oauth2")
            || path.startsWith("/login/oauth2");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        if (skip(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String auth = request.getHeader("Authorization");
        boolean needRefresh = false;
        if (auth == null || !auth.startsWith("Bearer ")) {
            needRefresh = true;
        } else {
            String AT = auth.substring("Bearer ".length());
            try {
                JWT.require(Algorithm.HMAC512(JwtProperties.SECRET)).build().verify(AT);
                filterChain.doFilter(request, response);
                return;
            } catch (TokenExpiredException e) {
                needRefresh = true;
            } catch (Exception e) {
                needRefresh = true;
            }
        }
        if (!needRefresh) {
            filterChain.doFilter(request, response);
            return;
        }

        var rtCookie = CookieUtils.getCookie(request, "refresh_token").orElse(null);
        if (rtCookie == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String newAt = tokenRotationService.rotateTokens(rtCookie.getValue(), response);
            CustomHttpServletRequest customRequest = new CustomHttpServletRequest(request);
            customRequest.putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAt);
            filterChain.doFilter(customRequest, response);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
        }
    }
}
