package com.icc.qasker.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestLoginController {

    @GetMapping("/login")
    public String loginPage() {
        return "<h1>Google 로그인</h1><a href='/oauth2/authorization/google'>구글로 로그인</a>";
    }
}
