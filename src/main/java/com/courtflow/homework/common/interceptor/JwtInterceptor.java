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
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        String token = authHeader.replace("Bearer ", "");
        try {
            Claims claims = jwtUtils.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            if (userId == null && claims.getSubject() != null) {
                userId = Long.parseLong(claims.getSubject());
            }
            String role = claims.get("role", String.class);

            UserContext.set(userId, role);
            return true;

        } catch (ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        } catch (JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        UserContext.clear();
    }
}
