package com.icc.qasker.auth.service.mock;

import com.icc.qasker.auth.UserService;
import com.icc.qasker.auth.service.UserServiceImpl;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 부하 트레이스용 user mock(@Profile("mock")). 읽기는 실 서비스에 위임하고, 닉네임 변경(기존 행 UPDATE)은 변경→원복으로 순증 0을 유지한다(별개
 * 트랜잭션 2건이라 UPDATE 두 번이 실 URI로 태깅되고 데이터는 불변).
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockUserService implements UserService {

  private final UserServiceImpl real;

  @Override
  public String getUserNickname(String userId) {
    return real.getUserNickname(userId);
  }

  @Override
  public void checkUserExists(String userId) {
    real.checkUserExists(userId);
  }

  @Override
  public void changeNickName(String userId, String nickname) {
    String original = real.getUserNickname(userId);
    real.changeNickName(userId, nickname);
    real.changeNickName(userId, original);
  }

  @Override
  public Map<String, String> getNickNames(List<String> userIds) {
    return real.getNickNames(userIds);
  }
}
