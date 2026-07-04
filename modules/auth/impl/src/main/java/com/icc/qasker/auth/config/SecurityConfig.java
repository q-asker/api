package com.icc.qasker.auth.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.icc.qasker.auth.component.JwtProvider;
import com.icc.qasker.auth.config.security.SecurityErrorResponder;
import com.icc.qasker.auth.config.security.filter.JwtTokenAuthenticationFilter;
import com.icc.qasker.auth.config.security.filter.RateLimitFilter;
import com.icc.qasker.auth.config.security.handler.OAuth2LoginSuccessHandler;
import com.icc.qasker.auth.config.security.service.PrincipalOAuth2UserService;
import com.icc.qasker.auth.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
  private final RateLimitFilter rateLimitFilter;
  private final JwtProvider jwtProvider;
  private final SecurityErrorResponder securityErrorResponder;

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }

  @Bean
  public SecurityFilterChain apiFilterChain(
      HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    http.cors(withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authException) ->
                            securityErrorResponder.writeUnauthorized(response))
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) ->
                            securityErrorResponder.writeForbidden(response)))
        .addFilterBefore(
            new JwtTokenAuthenticationFilter(authenticationManager, userRepository, jwtProvider),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, JwtTokenAuthenticationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/boards/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/boards/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/boards/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/boards/**")
                    .authenticated()
                    .requestMatchers("/history", "/history/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/problem-set/*/title")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .oauth2Login(
            oauth ->
                oauth
                    .userInfoEndpoint(user -> user.userService(principalOauth2UserService))
                    .successHandler(oAuth2LoginSuccessHandler));
    return http.build();
  }
}
