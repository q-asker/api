package com.icc.qasker.auth.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.auth.dto.principal.PrincipalDetails;
import com.icc.qasker.auth.dto.response.LoginResponse;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.service.TokenRotationService;
import jakarta.servlet.ServletException;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
        Authentication authentication) throws IOException, ServletException {

        PrincipalDetails principal = (PrincipalDetails) authentication.getPrincipal();
        User user = principal.getUser();
        tokenRotationService.issueTokens(user.getUserId(), response);
        LoginResponse loginResponse = LoginResponse.builder()
            .nickname(user.getNickname())
            .build();
        response.getWriter().write(objectMapper.writeValueAsString(loginResponse));
    }
}
