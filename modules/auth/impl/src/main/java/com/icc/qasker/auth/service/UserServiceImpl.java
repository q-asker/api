package com.icc.qasker.auth.service;

import com.icc.qasker.auth.UserService;
import com.icc.qasker.auth.dto.response.UserInfoResponse;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;

  @Override
  public String getUserNickname(String userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.USER_NOT_FOUND));
    return user.getNickname();
  }

  @Override
  public void checkUserExists(String userId) {
    if (!userRepository.existsById(userId)) {
      throw new CustomException(ExceptionMessage.USER_NOT_FOUND);
    }
  }

  @Override
  public Map<String, String> getNickNames(List<String> userIds) {
    List<UserInfoResponse> userInfoResponses = userRepository.findNicknamesByUserIds(userIds);
    return userInfoResponses.stream()
        .collect(Collectors.toMap(UserInfoResponse::userId, UserInfoResponse::nickname));
  }
}
