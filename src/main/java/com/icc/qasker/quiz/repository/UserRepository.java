package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

}
