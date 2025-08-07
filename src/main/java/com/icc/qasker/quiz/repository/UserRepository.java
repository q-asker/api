package com.icc.qasker.quiz.repository;

import com.icc.qasker.quiz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    public User findByUsername(String username);

}
