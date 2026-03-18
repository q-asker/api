package com.icc.qasker.util.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서버 상태 확인 API")
@RestController
public class HelloController {

  @Value("${spring.profiles.active:default}")
  private String activeProfile;

  @Operation(summary = "서버 상태 및 활성 프로필을 확인한다")
  @GetMapping("/status")
  public Map<String, Object> status() {
    Map<String, Object> response = new HashMap<>();

    response.put("profile", activeProfile);
    response.put("status", "UP");

    return response;
  }
}
