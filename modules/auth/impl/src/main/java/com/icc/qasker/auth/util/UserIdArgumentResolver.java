package com.icc.qasker.auth.util;

import com.icc.qasker.auth.component.PrincipalExtractor;
import com.icc.qasker.global.annotation.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class UserIdArgumentResolver implements HandlerMethodArgumentResolver {

  private final PrincipalExtractor principalExtractor;

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(UserId.class)
        && parameter.getParameterType().equals(String.class);
  }

  @Override
  public @Nullable Object resolveArgument(
      MethodParameter parameter,
      @Nullable ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      @Nullable WebDataBinderFactory binderFactory) {
    return principalExtractor
        .extractUserId(SecurityContextHolder.getContext().getAuthentication())
        .orElse(null);
  }
}
