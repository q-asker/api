package com.icc.qasker.auth.config.security.service;

import com.icc.qasker.auth.config.security.provider.GoogleUserInfo;
import com.icc.qasker.auth.config.security.provider.KakaoUserInfo;
import com.icc.qasker.auth.config.security.provider.OAuth2UserInfo;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.principal.UserPrincipal;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.auth.util.NicknameGenerateUtil;
import lombok.AllArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class PrincipalOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
        throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        OAuth2UserInfo oAuth2UserInfo = null;
        if (userRequest.getClientRegistration().getRegistrationId().equals("google")) {
            oAuth2UserInfo = new GoogleUserInfo(oAuth2User.getAttributes());
        } else if (userRequest.getClientRegistration().getRegistrationId()
            .equals("kakao")) {
            oAuth2UserInfo = new KakaoUserInfo(oAuth2User.getAttributes());
        }
        String userId = oAuth2UserInfo.getProvider() + "_" + oAuth2UserInfo.getProviderId();
        String provider = oAuth2UserInfo.getProvider();
        String nickname = NicknameGenerateUtil.generate();
        String role = "ROLE_USER";
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            user = User.builder()
                .userId(userId)
                .role(role)
                .nickname(nickname)
                .provider(provider)
                .build();
            userRepository.save(user);
        }
        return new UserPrincipal(user, oAuth2User.getAttributes());
    }
}