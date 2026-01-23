package com.icc.qasker.auth.config.security.handler;

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

    /**
     * Handles post-login actions after successful OAuth2 authentication.
     *
     * Issues a refresh token for the authenticated user and redirects the client to the frontend login redirect URL.
     *
     * @param request        the HTTP servlet request for the current authentication
     * @param response       the HTTP servlet response used to issue the refresh token and perform the redirect
     * @param authentication the completed Authentication containing the authenticated principal
     * @throws IOException if sending the redirect or writing to the response fails
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication) throws IOException {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();
        tokenRotationService.issueRefreshToken(user.getUserId(), response);
        response.sendRedirect(qAskerProperties.getFrontendDeployUrl() + "/login/redirect");
    }
}