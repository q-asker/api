//package com.icc.qasker.auth.filter;
//
//import com.auth0.jwt.JWT;
//import com.auth0.jwt.algorithms.Algorithm;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.icc.qasker.auth.dto.principal.PrincipalDetails;
//import com.icc.qasker.auth.entity.User;
//import com.icc.qasker.auth.utils.JwtProperties;
//import com.icc.qasker.global.error.CustomException;
//import com.icc.qasker.global.error.ExceptionMessage;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import java.io.IOException;
//import java.util.Date;
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.AuthenticationException;
//import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
//
//@RequiredArgsConstructor
//public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
//
//    private final AuthenticationManager authenticationManager;
//    private final JwtProperties jwtProperties;
//
//    @Override
//    public Authentication attemptAuthentication(HttpServletRequest request,
//        HttpServletResponse response) throws AuthenticationException {
//
//        System.out.println("로그인 시도");
//
//        ObjectMapper mapper = new ObjectMapper();
//        User user = null;
//        try {
//            user = mapper.readValue(request.getInputStream(), User.class);
//        } catch (IOException e) {
//            e.printStackTrace();
//            throw new CustomException(ExceptionMessage.DEFAULT_ERROR);
//        }
//
//        UsernamePasswordAuthenticationToken authRequestToken = new UsernamePasswordAuthenticationToken(
//            user.getUserId(), user.getPassword());
//
//        Authentication auth = authenticationManager.authenticate(authRequestToken);
//        PrincipalDetails principalDetails = (PrincipalDetails) auth.getPrincipal();
//        System.out.println("principalDetails = " + principalDetails.getUser().getUserId());
//        return auth;
//    }
//
//    @Override
//    protected void successfulAuthentication(HttpServletRequest request,
//        HttpServletResponse response, FilterChain chain, Authentication authResult)
//        throws IOException, ServletException {
//
//        PrincipalDetails principalDetails = (PrincipalDetails) authResult.getPrincipal();
//        String jwtToken = JWT.create()
//            .withSubject(principalDetails.getUser().getUserId())
//            .withExpiresAt(
//                new Date(
//                    System.currentTimeMillis() + jwtProperties.getAccessExpirationTime()))
//            .withClaim("id", principalDetails.getUser().getUserId())
/// /            .withClaim("username", principalDetails.getUser().getUserId())
//            .sign(Algorithm.HMAC512(jwtProperties.getSecret()));
//        System.out.println("jwtToken = " + jwtToken);
//
//        response.addHeader("Authorization",
//            "Bearer " + jwtToken);
//
//    }
//}
