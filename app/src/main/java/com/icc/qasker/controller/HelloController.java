package com.icc.qasker.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

  @Value("${spring.profiles.active:default}")
  private String activeProfile;

  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of("profile", activeProfile, "status", "UP");
  }
}
