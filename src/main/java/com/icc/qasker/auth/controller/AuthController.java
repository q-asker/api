package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.dto.request.JoinRequest;
import com.icc.qasker.auth.dto.request.LoginRequest;
import com.icc.qasker.auth.dto.response.LoginResponse;
import com.icc.qasker.auth.service.NormalJoinService;
import com.icc.qasker.auth.service.NormalLoginService;
import com.icc.qasker.auth.service.RefreshTokenService;
import com.icc.qasker.auth.utils.CookieUtils;
import com.icc.qasker.auth.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final RefreshTokenService refreshService;
    private final NormalJoinService normalJoinService;
    private final NormalLoginService normalLoginService;

    @PostMapping("/join")
    public ResponseEntity<?> normalJoin(@RequestBody JoinRequest normalJoinRequest) {
        normalJoinService.register(normalJoinRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> normalLogin(@RequestBody LoginRequest normalLoginRequest) {
        normalLoginService.check(normalLoginRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        // 1. 쿠키에서 RT 추출
        String oldRt = CookieUtils.getCookie(request, "refresh_token")
            .orElseThrow(() -> new RuntimeException("RT not found")).getValue();

        // 2. RT 회전
        var result = refreshService.validateAndRotate(oldRt);

        // 3. AT 발급
        User user = user
    }

}
