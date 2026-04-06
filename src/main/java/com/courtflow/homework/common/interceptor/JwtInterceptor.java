package com.courtflow.homework.common.interceptor;

import com.courtflow.homework.common.annonation.CheckToken;
import com.courtflow.homework.common.context.UserContext;
import com.courtflow.homework.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private JwtUtils jwtUtils;

    @Autowired
    public JwtInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        if (uri.contains("/actuator/prometheus")) {
            return true;
        }

        if(!(handler instanceof HandlerMethod handlerMethod)){
            return true;
        }

        CheckToken methodAnnotation = handlerMethod.getMethodAnnotation(CheckToken.class);
        CheckToken classAnnotation = handlerMethod.getBeanType().getAnnotation(CheckToken.class);

        boolean required = true;
        if (methodAnnotation != null) {
            required = methodAnnotation.required();
        } else if (classAnnotation != null) {
            required = classAnnotation.required();
        }

        if(!required){
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authHeader.replace("Bearer ", "");
        try {
            Claims claims = jwtUtils.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            Integer role = claims.get("role", Integer.class);

            UserContext.set(userId, role);
            return true;

        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException e) {
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        UserContext.clear();
    }
}
