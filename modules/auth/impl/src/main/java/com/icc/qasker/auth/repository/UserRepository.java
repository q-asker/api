package com.icc.qasker.auth.repository;

import com.icc.qasker.auth.dto.response.UserInfoResponse;
import com.icc.qasker.auth.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

  boolean existsByUserId(String userId);

  @Query(
      "SELECT new com.icc.qasker.auth.dto.response.UserInfoResponse(u.userId, u.nickname) "
          + "FROM User u WHERE u.userId IN :userIds")
  List<UserInfoResponse> findNicknamesByUserIds(@Param("userIds") List<String> userIds);
}
