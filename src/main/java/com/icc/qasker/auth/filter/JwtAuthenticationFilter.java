package com.icc.qasker.auth.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.auth.oauth.principal.PrincipalDetails;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final JwtProperties jwtProperties;

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
        HttpServletResponse response) throws AuthenticationException {

        System.out.println("로그인 시도");

        ObjectMapper mapper = new ObjectMapper();
        User user = null;
        try {
            user = mapper.readValue(request.getInputStream(), User.class);
        } catch (IOException e) {
            e.printStackTrace();
            throw new CustomException(ExceptionMessage.DEFAULT_ERROR);
        }

        UsernamePasswordAuthenticationToken authRequestToken = new UsernamePasswordAuthenticationToken(
            user.getId(), user.getPassword());

        Authentication auth = authenticationManager.authenticate(authRequestToken);
        PrincipalDetails principalDetails = (PrincipalDetails) auth.getPrincipal();
        System.out.println("principalDetails = " + principalDetails.getUser().getUsername());
        return auth;
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request,
        HttpServletResponse response, FilterChain chain, Authentication authResult)
        throws IOException, ServletException {

        PrincipalDetails principalDetails = (PrincipalDetails) authResult.getPrincipal();
        String jwtToken = JWT.create()
            .withSubject(principalDetails.getUser().getUsername())
            .withExpiresAt(
                new Date(
                    System.currentTimeMillis() + jwtProperties.getExpirationTime()))
            .withClaim("id", principalDetails.getUser().getId())
            .withClaim("username", principalDetails.getUser().getUsername())
            .sign(Algorithm.HMAC512(jwtProperties.getSecret()));
        System.out.println("jwtToken = " + jwtToken);

        response.addHeader("Authorization",
            "Bearer " + jwtToken);

    }
}
