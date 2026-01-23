package com.icc.qasker.auth.config;

import com.icc.qasker.auth.config.security.filter.JwtTokenAuthenticationFilter;
import com.icc.qasker.auth.config.security.handler.OAuth2LoginSuccessHandler;
import com.icc.qasker.auth.config.security.service.PrincipalOAuth2UserService;
import com.icc.qasker.auth.repository.UserRepository;
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
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final PrincipalOAuth2UserService principalOauth2UserService;

    /**
     * Exposes the AuthenticationManager from the provided AuthenticationConfiguration as a Spring bean.
     *
     * @param authenticationConfiguration the AuthenticationConfiguration to obtain the AuthenticationManager from
     * @return the configured AuthenticationManager for the application
     * @throws Exception if the AuthenticationManager cannot be obtained from the provided configuration
     */
    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Configures and returns the application's HTTP security filter chain.
     *
     * <p>The chain enforces stateless sessions, disables CSRF, form login, and HTTP Basic,
     * injects a JWT authentication filter, configures OAuth2 login with a custom user service
     * and success handler, and provides custom JSON responses for unauthorized and forbidden errors.
     *
     * @return the configured SecurityFilterChain
     * @throws Exception if the HttpSecurity configuration cannot be built
     */
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
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/statistics/**", "/test").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(user -> user.userService(principalOauth2UserService))
                .successHandler(oAuth2LoginSuccessHandler)
            );
        return http.build();
    }

    /**
     * Writes a 401 Unauthorized JSON error response to the provided HttpServletResponse.
     *
     * @param response the HttpServletResponse to modify; its status is set to 401 and a JSON
     *                 body with an authorization error message is written
     * @throws IOException if an I/O error occurs while writing the response
     */
    public void createUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            "{\"message\": \"" + ExceptionMessage.UNAUTHORIZED.getMessage() + "\"}"
        );
    }

    /**
     * Send a 403 Forbidden JSON response indicating insufficient access.
     *
     * Sets the HTTP status to 403, the content type to "application/json;charset=UTF-8",
     * and writes a JSON object containing the `NOT_ENOUGH_ACCESS` message.
     *
     * @param response the HTTP response to modify
     * @throws IOException if writing to the response fails
     */
    public void createForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            "{\"message\": \"" + ExceptionMessage.NOT_ENOUGH_ACCESS.getMessage() + "\"}"
        );
    }
}