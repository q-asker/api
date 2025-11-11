package com.icc.qasker.service;

import com.icc.qasker.NormalJoinService;
import com.icc.qasker.auth.dto.request.JoinRequest;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.auth.util.NicknameGenerateUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NormalJoinServiceImpl implements NormalJoinService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public void register(JoinRequest normalJoinRequest) {
        String userId = normalJoinRequest.getUserId();

        if (userRepository.existsByUserId(userId)) {
            throw new CustomException(ExceptionMessage.DUPLICATE_USERNAME);
        }

        String nickname = NicknameGenerateUtil.generate();
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

