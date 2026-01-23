package com.icc.qasker.auth.config;

import com.icc.qasker.auth.resolver.UserIdArgumentResolver;
import com.icc.qasker.global.properties.QAskerProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@AllArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserIdArgumentResolver userIdArgumentResolver;
    private final QAskerProperties qAskerProperties;

    /**
     * Configure global CORS settings for all request paths.
     *
     * <p>Allows requests from the frontend development and deployed origins, permits
     * GET, POST, PUT and DELETE methods, enables credentials, and sets preflight
     * cache duration to 3600 seconds.</p>
     *
     * @param registry the CorsRegistry to configure
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(qAskerProperties.getFrontendDevUrl(),
                qAskerProperties.getFrontendDeployUrl())
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowCredentials(true)
            .maxAge(3600);
    }

    /**
     * Registers the configured UserIdArgumentResolver with Spring MVC's argument resolver list.
     *
     * @param resolvers the mutable list of HandlerMethodArgumentResolver instances provided by Spring MVC;
     *                  the UserIdArgumentResolver will be appended to this list
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(userIdArgumentResolver);
    }
}