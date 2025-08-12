package com.icc.qasker.auth.normal.controller;

import com.icc.qasker.auth.normal.dto.request.JoinRequest;
import com.icc.qasker.auth.normal.dto.request.LoginRequest;
import com.icc.qasker.auth.normal.dto.response.LoginResponse;
import com.icc.qasker.auth.normal.service.NormalJoinService;
import com.icc.qasker.auth.normal.service.NormalLoginService;
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

}
