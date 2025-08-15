package com.icc.qasker.auth.filter;

import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.repository.RefreshTokenRepository.UserRepository;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

// 승인 필터
//
public class JwtAuthorizationFilter extends BasicAuthenticationFilter {

    private UserRepository userRepository;

    public JwtAuthorizationFilter(AuthenticationManager authManager,
        UserRepository userRepository) {
        super(authManager);
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws IOException, ServletException {

        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String accessToken = authorizationHeader.substring("Bearer ".length());

        if (userId != null) {
            // 1. 사용자 조회
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ExceptionMessage.INVALID_JWT));
        }
        chain.doFilter(request, response);
    }


}
