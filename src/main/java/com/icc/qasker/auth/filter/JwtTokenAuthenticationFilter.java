package com.icc.qasker.auth.filter;


import static com.auth0.jwt.JWT.require;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.auth.utils.JwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;


public class JwtTokenAuthenticationFilter extends BasicAuthenticationFilter {

    private final UserRepository userRepository;

    public JwtTokenAuthenticationFilter(AuthenticationManager authManager,
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
        try {
            var decoded = require(Algorithm.HMAC512(JwtProperties.SECRET)).build()
                .verify(accessToken);

            String userId = decoded.getClaim("userId").asString();
            if (userId == null || userId.isBlank()) {
                chain.doFilter(request, response);
                return;
            }

            Authentication existing = SecurityContextHolder.getContext().getAuthentication();
            if (existing != null && existing.isAuthenticated()) {
                chain.doFilter(request, response);
                return;
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                chain.doFilter(request, response);
                return;
            }
            String role = Objects.toString(user.getRole(), "ROLE_USER");
            var authorities = List.of(new SimpleGrantedAuthority(role));

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null,
                    authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (TokenExpiredException ex) {
            chain.doFilter(request, response);
        }
    }
}
