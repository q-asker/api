package com.icc.qasker.auth.repository;

import com.icc.qasker.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByUserId(String userId);
}
