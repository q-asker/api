package com.icc.qasker.auth.config;

import com.icc.qasker.auth.oauth.user.PrincipalOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity // Spring Security 필터 체인 커스텀 후 등록
public class SecurityConfig {

    private final PrincipalOAuth2UserService principalOauth2UserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // csrf 비활성화
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/statistics/**").authenticated() // 추후 통계 기능 인증 필요
                .requestMatchers("/admin/**").hasRole("ADMIN") // 관리자 페이지
                .anyRequest().permitAll() // 나머지 모두 허용
            )
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(user -> user
                    .userService(principalOauth2UserService)
                )
            );
        return http.build();
    }
}
