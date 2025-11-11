package com.icc.qasker.service;

import com.icc.qasker.NormalLoginService;
import com.icc.qasker.auth.dto.request.LoginRequest;
import com.icc.qasker.auth.dto.response.LoginResponse;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.error.ExceptionMessage;
import com.icc.qasker.global.error.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NormalLoginServiceImpl implements NormalLoginService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public LoginResponse getNickname(LoginRequest loginRequest) {
        String userId = loginRequest.getUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.USER_NOT_FOUND));

        if (!bCryptPasswordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new CustomException(ExceptionMessage.INVALID_PASSWORD);
        }
        System.out.println("로그인 완료: " + user.getUserId() + " 닉네임: " + user.getNickname());
        return LoginResponse.builder()
            .nickname(user.getNickname()).build();
    }
}

