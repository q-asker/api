package com.icc.qasker.auth.config;

import com.icc.qasker.auth.oauth.handler.OAuth2LoginSuccessHandler;
import com.icc.qasker.auth.oauth.service.PrincipalOAuth2UserService;
import com.icc.qasker.auth.token.JwtAuthorizationFilter;
import com.icc.qasker.quiz.repository.UserRepository;
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
    // private final JwtProperties jwtProperties;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationManager authenticationManager = authenticationConfiguration.getAuthenticationManager();

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // .addFilter(new JwtAuthenticationFilter(authenticationManager, jwtProperties)) // handler로 변경 예정
            .addFilter(new JwtAuthorizationFilter(authenticationManager, userRepository))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/statistics/**").authenticated() // 추후 통계 기능 인증 필요
                .requestMatchers("/admin/**").hasRole("ADMIN") // 관리자 페이지
                .anyRequest().permitAll() // 나머지 모두 허용
            )
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(user -> user
                    .userService(principalOauth2UserService)
                )
                .successHandler(oAuth2LoginSuccessHandler)
            );
        return http.build();
    }
}
