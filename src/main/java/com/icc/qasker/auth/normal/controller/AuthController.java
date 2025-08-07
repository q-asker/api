package com.icc.qasker.auth.normal.controller;

import com.icc.qasker.auth.normal.Service.NormalJoinService;
import com.icc.qasker.auth.normal.Service.NormalLoginService;
import com.icc.qasker.auth.normal.dto.request.NormalJoinRequest;
import com.icc.qasker.auth.normal.dto.request.NormalLoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final NormalJoinService normalJoinService;
    private final NormalLoginService normalLoginService;

    @PostMapping("/join")
    public ResponseEntity<?> normalJoin(@RequestBody NormalJoinRequest normalJoinRequest) {
        normalJoinService.register(normalJoinRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> normalLogin(@RequestBody NormalLoginRequest normalLoginRequest) {
        normalLoginService.check(normalLoginRequest);
        return ResponseEntity.ok().build();
    }

}
