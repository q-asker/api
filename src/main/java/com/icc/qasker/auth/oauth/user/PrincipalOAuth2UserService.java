package com.icc.qasker.auth.oauth.user;

import com.icc.qasker.auth.oauth.provider.GoogleUserInfo;
import com.icc.qasker.auth.oauth.provider.OAuth2UserInfo;
import com.icc.qasker.auth.principal.PrincipalDetails;
import com.icc.qasker.quiz.entity.User;
import com.icc.qasker.quiz.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class PrincipalOAuth2UserService extends DefaultOAuth2UserService {

    private UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // try sign in
        OAuth2User oAuth2User = super.loadUser(userRequest);
        System.out.println("principal: " + oAuth2User);
        OAuth2UserInfo oAuth2UserInfo = null;
        // OAuth login
        if (userRequest.getClientRegistration().getRegistrationId().equals("google")) { // google
            System.out.println("try google login");
            oAuth2UserInfo = new GoogleUserInfo(oAuth2User.getAttributes());
        } else {
            System.out.println("we don't support that site");
        }
        String provider = oAuth2UserInfo.getProvider();
        String providerId = oAuth2UserInfo.getProviderId();
        String username = oAuth2UserInfo.getName();
        String email = oAuth2UserInfo.getEmail();
        // password
        String role = "ROLE_USER";
        User user = userRepository.findByUsername(username);
        if (user == null) { // 회원가입 필요
            user = User.builder()
                .username(username)
                // .password(password)
                .email(email)
                .role(role)
                .provider(provider)
                .providerId(providerId)
                .build();
            userRepository.save(user);
        }
        return new PrincipalDetails(user, oAuth2User.getAttributes());

    }
}
