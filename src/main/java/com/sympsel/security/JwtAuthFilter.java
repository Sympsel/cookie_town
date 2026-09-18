package com.sympsel.security;

import com.sympsel.entitys.enums.Permission;
import com.sympsel.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT 认证过滤器：
 * - 对所有 /api/** 请求尝试解析 Authorization: Bearer <token>，成功则写入 UserContext；
 * - 写操作（POST/PUT/DELETE/PATCH）除注册、登录外都要求已认证，否则直接返回 401；
 * - 所有 GET 读操作公开；请求结束后清理 ThreadLocal，避免线程池复用导致的上下文泄漏。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // 非 API 路径或 CORS 预检直接放行
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        boolean authenticated = tryAuthenticate(request);
        try {
            if (!authenticated && requiresAuth(path, method)) {
                writeUnauthorized(response, "未认证：请先登录并携带有效的 Bearer Token");
                return;
            }
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 尝试从请求头解析并验签 token，成功则写入 UserContext 并返回 true。
     */
    private boolean tryAuthenticate(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            return false;
        }
        try {
            Claims claims = jwtService.parse(token);
            if (!userRepository.existsById(claims.getSubject())) {
                UserContext.clear();
                return false;   // 走既有的 401 分支：未认证
            }
            String permission = claims.get("permission", String.class);
            UserContext.set(new UserContext.CurrentUser(
                    claims.getSubject(),
                    claims.get("name", String.class),
                    permission == null ? null : Permission.valueOf(permission)));
            return true;
        } catch (RuntimeException e) {
            UserContext.clear();
            return false;
        }
    }

    /**
     * 判断某请求是否需要认证：注册与登录公开，GET 读操作公开，其余写操作需要认证。
     */
    private boolean requiresAuth(String path, String method) {
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        if (path.startsWith("/api/auth/")) {
            return false;
        }
        // 用户注册公开（POST /api/users），其余对 /api/users 的写操作需要认证
        return !("/api/users".equals(path) && "POST".equalsIgnoreCase(method));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8));
    }
}
