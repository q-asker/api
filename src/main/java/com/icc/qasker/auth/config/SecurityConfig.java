package com.icc.qasker.auth.config;

import com.icc.qasker.auth.filter.JwtTokenAuthenticationFilter;
import com.icc.qasker.auth.filter.RefreshRotationFilter;
import com.icc.qasker.auth.repository.RefreshTokenRepository.UserRepository;
import com.icc.qasker.auth.service.AccessTokenService;
import com.icc.qasker.auth.service.PrincipalOAuth2UserService;
import com.icc.qasker.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final PrincipalOAuth2UserService principalOauth2UserService;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationManager authenticationManager = authenticationConfiguration.getAuthenticationManager();

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .addFilterBefore(new RefreshRotationFilter(refreshTokenService, accessTokenService),
                JwtTokenAuthenticationFilter.class)
            .addFilter(new JwtTokenAuthenticationFilter(authenticationManager, userRepository))
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
