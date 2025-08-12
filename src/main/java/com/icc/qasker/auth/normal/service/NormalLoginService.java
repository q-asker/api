package com.icc.qasker.auth.normal.service;

import com.icc.qasker.auth.normal.dto.request.LoginRequest;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.entity.User;
import com.icc.qasker.quiz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NormalLoginService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public void check(LoginRequest normalLoginRequest) {
        String username = normalLoginRequest.getUsername();
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new CustomException(ExceptionMessage.USER_NOT_FOUND));

        if (!bCryptPasswordEncoder.matches(normalLoginRequest.getPassword(), user.getPassword())) {
            throw new CustomException(ExceptionMessage.INVALID_PASSWORD);
        }
        System.out.println("로그인 완료: " + user.getUsername());
    }
}
