package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.LogoutService;
import com.icc.qasker.auth.TokenRotationService;
import com.icc.qasker.auth.dto.response.RotateTokenResponse;
import com.icc.qasker.auth.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final TokenRotationService tokenRotationService;
    private final LogoutService logoutService;

    /**
     * Health-check endpoint for the auth controller that responds with HTTP 200 OK.
     *
     * @return ResponseEntity with HTTP 200 OK and no body.
     */
    @GetMapping("/test")
    public ResponseEntity<?> test() {
        System.out.println("test 성공");
        return ResponseEntity.ok().build();
    }

    /**
     * Rotate authentication tokens using the refresh token stored in the "refresh_token" cookie and send the new tokens in the response.
     *
     * @param request  the incoming HTTP request which should contain the "refresh_token" cookie
     * @param response the HTTP response where the rotated tokens (e.g., new cookies) will be written
     * @return a RotateTokenResponse containing the newly issued tokens and related metadata
     */
    @PostMapping("/refresh")
    public ResponseEntity<RotateTokenResponse> refresh(HttpServletRequest request,
        HttpServletResponse response) {
        var rtCookie = CookieUtil.getCookie(request, "refresh_token").orElse(null);
        return ResponseEntity.ok(tokenRotationService.rotateTokens(rtCookie.getValue(), response));
    }

    /**
     * Invalidates the current user's authentication (clears session/cookies as applicable) and returns success.
     *
     * @return an HTTP 200 OK response with an empty body
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        logoutService.logout(request, response);
        return ResponseEntity.ok().build();
    }
}