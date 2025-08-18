package com.icc.qasker.auth.service;

import com.icc.qasker.auth.dto.request.JoinRequest;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.auth.utils.NicknameGenerator;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NormalJoinService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public void register(JoinRequest normalJoinRequest) {
        String userId = normalJoinRequest.getUsername();

        if (userRepository.existsByUserId(userId)) {
            throw new CustomException(ExceptionMessage.DUPLICATE_USERNAME);
        }

        String nickname = NicknameGenerator.generate();
        String password = bCryptPasswordEncoder.encode(normalJoinRequest.getPassword());
        User user = User.builder()
            .userId(userId)
            .password(password)
            .role("ROLE_USER")
            .provider(null)
            .nickname(nickname)
            .build();
        userRepository.save(user);
        System.out.println("기본 회원가입 완료, " + nickname);
    }
}
