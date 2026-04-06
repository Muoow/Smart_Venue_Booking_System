package com.courtflow.homework.common.interceptor;

import com.courtflow.homework.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
@Slf4j
public class LogInterceptor implements HandlerInterceptor {

    private static final String START_TIME = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator") || uri.startsWith("/prometheus")) {
            return true;
        }

        request.setAttribute(START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator") || uri.startsWith("/prometheus")) {
            return;
        }

        long start = (long) request.getAttribute(START_TIME);
        long cost = System.currentTimeMillis() - start;

        String method = request.getMethod();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);
        int status = response.getStatus();

        Long userId = null;
        try {
            userId = UserContext.getUserId();
        } catch (Exception ignored) {
        }

        String params = buildParams(request);

        if (ex == null) {
            log.info(
                    "API_LOG | userId={} | ip={} | {} {} | status={} | cost={}ms | params={}",
                    userId,
                    clientIp,
                    method,
                    uri + (queryString != null ? "?" + queryString : ""),
                    status,
                    cost,
                    params
            );
        } else {
            log.error(
                    "API_ERROR | userId={} | ip={} | {} {} | status={} | cost={}ms | params={} | error={}",
                    userId,
                    clientIp,
                    method,
                    uri,
                    status,
                    cost,
                    params,
                    ex.getMessage(),
                    ex
            );
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String buildParams(HttpServletRequest request) {
        Map<String, String[]> paramMap = request.getParameterMap();

        if (paramMap.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        paramMap.forEach((key, value) -> {
            sb.append(key).append("=");
            if (value != null && value.length > 0) {
                sb.append(String.join(",", value));
            }
            sb.append(" ");
        });

        return sb.toString().trim();
    }
}
