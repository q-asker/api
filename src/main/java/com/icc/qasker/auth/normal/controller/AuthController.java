package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.dto.request.NormalJoinRequest;
import com.icc.qasker.auth.utils.UsernameGenerator;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.entity.User;
import com.icc.qasker.quiz.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final UserRepository userRepository;

    public AuthController(BCryptPasswordEncoder bCryptPasswordEncoder,
        UserRepository userRepository) {
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.userRepository = userRepository;
    }

    @PostMapping("/join")
    public ResponseEntity<?> normalJoin(@ResponseBody NormalJoinRequest normalJoinRequest) {
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
        return ResponseEntity.ok();
    }

}
