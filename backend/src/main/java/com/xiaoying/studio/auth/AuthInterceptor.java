package com.xiaoying.studio.auth;

import com.xiaoying.studio.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从 Authorization: Bearer &lt;token&gt; 解析当前用户，写入 request attribute。
 * 登录接口（POST /api/session）放行，其余受保护接口没有有效令牌时返回 401。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR = "authUser";

    private final UserMapper userMapper;

    public AuthInterceptor(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = extractToken(request);
        AuthUser user = token == null ? null : userMapper.findByToken(token);
        if (user != null) {
            request.setAttribute(ATTR, user);
            return true;
        }
        boolean isLogin = "/api/session".equals(request.getRequestURI()) && HttpMethod.POST.matches(request.getMethod());
        if (isLogin) {
            return true;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
    }

    static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
