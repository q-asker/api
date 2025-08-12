package com.icc.qasker.auth.normal.service;

import com.icc.qasker.auth.normal.dto.request.JoinRequest;
import com.icc.qasker.auth.utils.NicknameGenerator;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.entity.User;
import com.icc.qasker.quiz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NormalJoinService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public void register(JoinRequest normalJoinRequest) {
        String username = normalJoinRequest.getUsername();

        if (userRepository.existsByUsername(username)) {
            throw new CustomException(ExceptionMessage.DUPLICATE_USERNAME);
        }

        String nickname = NicknameGenerator.generate();
        String password = bCryptPasswordEncoder.encode(normalJoinRequest.getPassword());
        User user = User.builder()
            .username(username)
            .password(password)
            .role("ROLE_USER")
            .provider(null)
            .nickname(nickname)
            .build();
        userRepository.save(user);
        System.out.println("기본 회원가입 완료, " + nickname);
    }
}
