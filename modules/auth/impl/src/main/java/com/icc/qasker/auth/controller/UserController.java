package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.UserService;
import com.icc.qasker.auth.dto.request.NicknameChangeRequest;
import com.icc.qasker.global.annotation.UserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PatchMapping("/nickname")
  public ResponseEntity<Void> changeNickName(
      @UserId String userId, @RequestBody @Valid NicknameChangeRequest request) {
    userService.changeNickName(userId, request.nickname());
    return ResponseEntity.ok().build();
  }
}
