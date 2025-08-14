package com.icc.qasker.auth.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.service.RefreshTokenService;
import com.icc.qasker.auth.token.CustomHttpServletRequest;
import com.icc.qasker.auth.utils.CookieUtils;
import com.icc.qasker.auth.utils.JwtProperties;
import com.icc.qasker.quiz.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class RefreshRotationFilter extends OncePerRequestFilter {

    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    private boolean skip(String path) {
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
            String at = auth.substring("Bearer ".length());
            try {
                JWT.require(Algorithm.HMAC512(jwtProperties.getSecret())).build().verify(at);
                filterChain.doFilter(request, response);
                return;
            } catch (TokenExpiredException e) {
                needRefresh = true;
            } catch (Exception e) {
                // 형식/서명 오류 → 회전 시도(정책에 따라 바로 401로 끊어도 됨)
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
            var newRtCookie = refreshTokenService.validateAndRotate(rtCookie.getValue());
            User user = userRepository.findById(newRtCookie.userId()).orElse(null);
            String newAt = JWT.create()
                .withSubject(user.getUserId())
                .withClaim("id", user.getUserId())
                .withClaim("role", user.getRole())
                .withExpiresAt(
                    new Date(System.currentTimeMillis() + jwtProperties.getAccessExpirationTime()))
                .sign(Algorithm.HMAC512(jwtProperties.getSecret()));

            response.setHeader(HttpHeaders.AUTHORIZATION, "Bear " + newAt);
            response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtils.buildCookies(newRtCookie.newRtPlain()).toString());
            CustomHttpServletRequest customRequest = new CustomHttpServletRequest(request);
            customRequest.putHeader(HttpHeaders.AUTHORIZATION, "Bear " + newAt);
            filterChain.doFilter(customRequest, response);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
        }
    }
}
