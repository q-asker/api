package com.icc.qasker.auth.security;

import com.icc.qasker.auth.TokenRotationService;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.auth.security.filter.JwtTokenAuthenticationFilter;
import com.icc.qasker.auth.security.filter.RefreshRotationFilter;
import com.icc.qasker.auth.security.handler.OAuth2LoginSuccessHandler;
import com.icc.qasker.auth.security.service.PrincipalOAuth2UserService;
import com.icc.qasker.global.error.ExceptionMessage;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfig {

    private final UserRepository userRepository;
    private final TokenRotationService tokenRotationService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final PrincipalOAuth2UserService principalOauth2UserService;

    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
        AuthenticationManager authenticationManager) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    createUnauthorizedResponse(response);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    createForbiddenResponse(response);
                })
            )
            .addFilterBefore(
                new JwtTokenAuthenticationFilter(authenticationManager, userRepository),
                UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new RefreshRotationFilter(tokenRotationService),
                JwtTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login/**", "/oauth2/**").permitAll()
                .requestMatchers("/statistics/**").authenticated()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/test").authenticated()
                .anyRequest().denyAll()
            )
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(user -> user.userService(principalOauth2UserService))
                .successHandler(oAuth2LoginSuccessHandler)
            );
        return http.build();
    }

    public void createUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            "{\"message\": \"" + ExceptionMessage.UNAUTHORIZED.getMessage() + "\"}"
        );
    }

    public void createForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            "{\"message\": \"" + ExceptionMessage.NOT_ENOUGH_ACCESS.getMessage() + "\"}"
        );
    }
}