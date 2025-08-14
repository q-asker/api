package com.icc.qasker.auth.service;

import com.icc.qasker.auth.dto.principal.PrincipalDetails;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.utils.NicknameGenerator;
import com.icc.qasker.auth.utils.provider.GoogleUserInfo;
import com.icc.qasker.auth.utils.provider.KakaoUserInfo;
import com.icc.qasker.auth.utils.provider.OAuth2UserInfo;
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
        String username = oAuth2UserInfo.getProvider() + "_" + oAuth2UserInfo.getProviderId();
        String provider = oAuth2UserInfo.getProvider();
        String nickname = NicknameGenerator.generate();
        String password = bCryptPasswordEncoder.encode("temporaryPassword");
        String role = "ROLE_USER";
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = User.builder()
                .username(username)
                .password(password)
                .role(role)
                .nickname(nickname)
                .provider(provider)
                .build();
            userRepository.save(user);
            System.out.println("oauth 회원가입 완료, " + provider + ", " + nickname);
        } else {
            System.out.println("이미 존재하는 회원");
        }
        return new PrincipalDetails(user, oAuth2User.getAttributes());
    }
}
