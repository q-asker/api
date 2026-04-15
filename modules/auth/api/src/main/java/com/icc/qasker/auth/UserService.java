package com.icc.qasker.auth;

import java.util.List;
import java.util.Map;

public interface UserService {

  String getUserNickname(String userId);

  void checkUserExists(String userId);

  void changeNickName(String userId, String nickname);

  Map<String, String> getNickNames(List<String> userIds);
}
