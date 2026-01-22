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

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        System.out.println("test 성공");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<RotateTokenResponse> refresh(HttpServletRequest request,
        HttpServletResponse response) {
        var rtCookie = CookieUtil.getCookie(request, "refresh_token").orElse(null);
        return ResponseEntity.ok(tokenRotationService.rotateTokens(rtCookie.getValue(), response));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        logoutService.logout(request, response);
        return ResponseEntity.ok().build();
    }
}
