package com.icc.qasker.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@RequiredArgsConstructor
public class TestController {

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        System.out.println("test");
        return ResponseEntity.ok().build();
    }
}
