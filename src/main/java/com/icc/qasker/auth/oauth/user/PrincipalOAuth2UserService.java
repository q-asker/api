package com.icc.qasker.auth.oauth.user;

import com.icc.qasker.auth.oauth.principal.PrincipalDetails;
import com.icc.qasker.auth.oauth.provider.GoogleUserInfo;
import com.icc.qasker.auth.oauth.provider.KakaoUserInfo;
import com.icc.qasker.auth.oauth.provider.OAuth2UserInfo;
import com.icc.qasker.quiz.entity.User;
import com.icc.qasker.quiz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PrincipalOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // try sign in
        OAuth2User oAuth2User = super.loadUser(userRequest);
        System.out.println("PrincipalOAuth2UserService's loadUser: " + oAuth2User);
        OAuth2UserInfo oAuth2UserInfo = null;
        // OAuth login
        if (userRequest.getClientRegistration().getRegistrationId().equals("google")) { // google
            System.out.println("try google login");
            oAuth2UserInfo = new GoogleUserInfo(oAuth2User.getAttributes());
        } else if (userRequest.getClientRegistration().getRegistrationId()
            .equals("kakao")) { // kakao
            System.out.println("try kakao login");
            oAuth2UserInfo = new KakaoUserInfo(oAuth2User.getAttributes());
        } else {
            System.out.println("we don't support that site");
        }
        String provider = oAuth2UserInfo.getProvider();
        String username = oAuth2UserInfo.getName();
        String password = bCryptPasswordEncoder.encode("temporaryPassword");
        String role = "ROLE_USER";
        User user = userRepository.findByUsername(username);
        if (user == null) { // need to sign up
            user = User.builder()
                .username(username)
                .password(password)
                .role(role)
                .provider(provider)
                .build();
            userRepository.save(user);
        }
        return new PrincipalDetails(user, oAuth2User.getAttributes());

    }
}
