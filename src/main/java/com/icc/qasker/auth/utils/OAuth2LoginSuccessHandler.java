package com.icc.qasker.auth.utils;

import com.icc.qasker.auth.dto.principal.PrincipalDetails;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.service.TokenRotationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final TokenRotationService tokenRotationService;
    @Value("${q-asker.frontend-deploy-url}")
    private String frontendDeployUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
        Authentication authentication) throws IOException {

        PrincipalDetails principal = (PrincipalDetails) authentication.getPrincipal();
        User user = principal.getUser();
        tokenRotationService.issueRefreshToken(user.getUserId(), response);
        response.sendRedirect(frontendDeployUrl);
    }
}
