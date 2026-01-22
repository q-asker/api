package com.icc.qasker.auth.security.handler;

import com.icc.qasker.auth.TokenRotationService;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.principal.UserPrincipal;
import com.icc.qasker.global.properties.QAskerProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final TokenRotationService tokenRotationService;
    private final QAskerProperties qAskerProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication) throws IOException {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();
        tokenRotationService.issueRefreshToken(user.getUserId(), response);
        response.sendRedirect(qAskerProperties.getFrontendDeployUrl());
    }
}