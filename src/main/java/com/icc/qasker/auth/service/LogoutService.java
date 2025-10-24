package com.icc.qasker.auth.service;

import com.icc.qasker.auth.utils.CookieUtils;
import com.icc.qasker.auth.utils.RefreshTokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutService {

    private final RefreshTokenUtils refreshTokenUtils;

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var rtCookie = CookieUtils.getCookie(request, "refresh_token").orElse(null);
        if (rtCookie != null) {
            refreshTokenUtils.revoke(rtCookie.getValue());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, CookieUtils.deleteRefreshCookie().toString());
    }

}
