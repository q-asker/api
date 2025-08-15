package com.icc.qasker.auth.repository;

import com.icc.qasker.auth.entity.RefreshToken;
import com.icc.qasker.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {

    interface UserRepository extends JpaRepository<User, String> {

        boolean existsUserId(String userId);

    }
}
