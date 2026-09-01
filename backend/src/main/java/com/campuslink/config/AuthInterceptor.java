package com.campuslink.config;

import com.campuslink.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equals(request.getMethod())) return true;
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(401, "请先登录"))); return false;
        }
        try {
            var identity = tokenService.verify(header.substring(7));
            AuthContext.set(identity.userId(), identity.role()); return true;
        } catch (Exception e) {
            response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(401, e.getMessage()))); return false;
        }
    }
    @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) { AuthContext.clear(); }
}
