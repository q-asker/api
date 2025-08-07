package com.icc.qasker.auth.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.entity.User;
import com.icc.qasker.quiz.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

public class JwtAuthorizationFilter extends BasicAuthenticationFilter {

    private UserRepository userRepository;
    private JwtProperties jwtProperties;

    public JwtAuthorizationFilter(AuthenticationManager authManager,
        UserRepository userRepository) {
        super(authManager);
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws IOException, ServletException {

        String jwtHeader = request.getHeader("Authorization");
        if (jwtHeader == null || !jwtHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String jwtToken = request.getHeader("Authorization").replace("Bearer ", "");
        String username =
            JWT.require(Algorithm.HMAC512(jwtProperties.getSecret())).build().verify(jwtToken)
                .getClaim("username").asString();
        if (username != null) {
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(
                    ExceptionMessage.INVALID_JWT));
        }
    }

}
