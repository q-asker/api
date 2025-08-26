package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.dto.request.JoinRequest;
import com.icc.qasker.auth.dto.request.LoginRequest;
import com.icc.qasker.auth.dto.response.LoginResponse;
import com.icc.qasker.auth.service.LogoutService;
import com.icc.qasker.auth.service.NormalJoinService;
import com.icc.qasker.auth.service.NormalLoginService;
import com.icc.qasker.auth.service.TokenRotationService;
import com.icc.qasker.auth.utils.CookieUtils;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> normalLogin(@RequestBody LoginRequest loginRequest,
        HttpServletResponse response) {
        tokenRotationService.issueTokens(loginRequest.getUserId(), response);
        return ResponseEntity.ok(normalLoginService.getNickname(loginRequest));
    }

    // 필터 내 자동회전 실패를 염두한 client용 수동 회전 엔드포인트
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        var rtCookie = CookieUtils.getCookie(request, "refresh_token")
            .orElseThrow(() -> new CustomException(ExceptionMessage.REFRESH_TOKEN_NOT_FOUND));

        try {
            tokenRotationService.rotateTokens(rtCookie.getValue(), response);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtils.deleteRefreshCookie().toString());
            throw new CustomException(ExceptionMessage.REFRESH_TOKEN_NOT_FOUND);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        logoutService.logout(request, response);
        return ResponseEntity.ok().build();
    }

}
