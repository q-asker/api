package com.icc.qasker.auth.normal.Service;

import com.icc.qasker.auth.normal.dto.request.NormalJoinRequest;
import com.icc.qasker.auth.utils.UsernameGenerator;
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

    public void register(NormalJoinRequest normalJoinRequest) {
        String id = "normal_" + normalJoinRequest.getId();

        if (userRepository.existsById(id)) {
            throw new CustomException(ExceptionMessage.DUPLICATE_ID);
        }

        String username = UsernameGenerator.generate();
        String password = bCryptPasswordEncoder.encode(normalJoinRequest.getPassword());

        User user = User.builder()
            .id(id)
            .username(username)
            .password(password)
            .role("ROLE_USER")
            .provider(null)
            .build();

        userRepository.save(user);
    }
}
