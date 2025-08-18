package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.dto.request.JoinRequest;
import com.icc.qasker.auth.dto.request.LoginRequest;
import com.icc.qasker.auth.dto.response.LoginResponse;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.auth.service.AccessTokenService;
import com.icc.qasker.auth.service.NormalJoinService;
import com.icc.qasker.auth.service.NormalLoginService;
import com.icc.qasker.auth.service.RefreshTokenService;
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

    private final RefreshTokenService refreshService;
    private final NormalJoinService normalJoinService;
    private final AccessTokenService accessTokenService;
    private final UserRepository userRepository;
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

    // 필터 내 자동회전 실패를 염두한 client용 수동 회전 엔드포인트
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        // 1. 쿠키에서 RT 추출
        var rtCookie = CookieUtils.getCookie(request, "refresh_token")
            .orElseThrow(() -> new CustomException(ExceptionMessage.REFRESH_TOKEN_NOT_FOUND));

        try {
            // 2. RT 회전(검증+폐기+신규 발급) + AT 생성
            var newRtCookie = refreshService.validateAndRotate(rtCookie.getValue());
            String newAt = accessTokenService.validateAndGenerate(newRtCookie.userId());
            if (newAt == null) {
                throw new CustomException(ExceptionMessage.USER_NOT_FOUND);
            }

            // 3. 응답 헤더/쿠키 세팅
            response.setHeader(HttpHeaders.AUTHORIZATION, "Bear " + newAt);
            response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtils.buildCookies(newRtCookie.newRtPlain()).toString());
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            // 예기치 않은 오류 - 쿠키 삭제 후 401/500 중 택1
            response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtils.deleteRefreshCookie().toString());
            return ResponseEntity.status(401).build();
        }
    }

}
