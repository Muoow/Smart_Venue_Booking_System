package com.courtflow.homework.common.config;

import com.courtflow.homework.common.interceptor.JwtInterceptor;
import com.courtflow.homework.common.interceptor.LogInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    private final LogInterceptor logInterceptor;

    @Autowired
    public WebConfig(JwtInterceptor jwtInterceptor, LogInterceptor logInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.logInterceptor = logInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/register");
        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/**");
    }
}
