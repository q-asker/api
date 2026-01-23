package com.icc.qasker.auth.resolver;

import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.principal.UserPrincipal;
import com.icc.qasker.global.annotation.UserId;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class UserIdArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * Determine whether a controller method parameter should be resolved as a user ID.
     *
     * @param parameter the method parameter to inspect
     * @return `true` if the parameter is annotated with `@UserId` and its type is `String`, `false` otherwise
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(UserId.class)
            && parameter.getParameterType().equals(String.class);
    }

    /**
     * Resolve the current authenticated user's ID for a method argument annotated with {@code @UserId}.
     *
     * @return the resolved userId string if the request is authenticated and the principal exposes a userId; `null` if unauthenticated, anonymous, or the principal does not provide a userId
     */
    @Override
    public @Nullable Object resolveArgument(MethodParameter parameter,
        @Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
        @Nullable WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user.getUserId();
        }
        if (principal instanceof UserPrincipal userPrinCipal) {
            return userPrinCipal.getUser().getUserId();
        }
        if (principal instanceof String userId && !userId.isBlank()
            && !"anonymousUser".equalsIgnoreCase(userId)) {
            return userId;
        }
        return null;
    }
}