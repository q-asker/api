package com.icc.qasker.auth.component;

import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenHandler {

  private final JwtProvider jwtProvider;
  private final UserRepository userRepository;

  public String validateAndGenerate(String userId) {
    return userRepository
        .findById(userId)
        .map(jwtProvider::sign)
        .orElseThrow(() -> new CustomException(ExceptionMessage.USER_NOT_FOUND));
  }
}
