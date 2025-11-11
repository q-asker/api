package com.icc.qasker.auth.controller;

import com.icc.qasker.LogoutService;
import com.icc.qasker.NormalJoinService;
import com.icc.qasker.NormalLoginService;
import com.icc.qasker.TokenRotationService;
import com.icc.qasker.auth.dto.request.JoinRequest;
import com.icc.qasker.auth.dto.request.LoginRequest;
import com.icc.qasker.auth.dto.response.LoginResponse;
import com.icc.qasker.auth.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final NormalJoinService normalJoinService;
    private final NormalLoginService normalLoginService;
    private final TokenRotationService tokenRotationService;
    private final LogoutService logoutService;

    @PostMapping("/join")
    public ResponseEntity<?> normalJoin(@RequestBody JoinRequest normalJoinRequest) {
        normalJoinService.register(normalJoinRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> normalLogin(@RequestBody LoginRequest loginRequest,
        HttpServletResponse response) {
        LoginResponse loginResponse = normalLoginService.getNickname(loginRequest);
        tokenRotationService.issueTokens(loginRequest.getUserId(), response);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        var rtCookie = CookieUtil.getCookie(request, "refresh_token").orElse(null);
        tokenRotationService.rotateTokens(rtCookie.getValue(), response);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        logoutService.logout(request, response);
        return ResponseEntity.ok().build();
    }

}
