package com.icc.qasker.auth.principal;

import com.icc.qasker.auth.entity.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Data
@AllArgsConstructor
public class UserPrincipal implements OAuth2User {

    private User user;
    private Map<String, Object> attributes;

    /**
     * Principal name that identifies the application user.
     *
     * @return the user's identifier (`userId`) as a String
     */
    @Override
    public String getName() {
        return user.getUserId();
    }

    /**
     * Provides the OAuth2 attributes associated with this principal.
     *
     * @return the attributes map supplied by the OAuth2 provider
     */
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Provide authorities granted to the user based on the user's role.
     *
     * @return a collection containing a single {@link GrantedAuthority} whose authority equals the user's role
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> collect = new ArrayList<GrantedAuthority>();
        collect.add(() -> user.getRole());
        return collect;
    }
}