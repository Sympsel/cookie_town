package com.sympsel.security;

import com.sympsel.entitys.enums.Permission;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HttpServletBean;

import java.util.Arrays;

@Component
public class PermissionInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (annotation == null) {
            return true;
        }
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            throw new UnauthorizedException("未认证：缺少有效的登录凭证");
        }
        Permission[] allowed = annotation.value();
        boolean permitted = user.permission() != null && Arrays.asList(allowed).contains(user.permission());
        if (!permitted) {
            throw new ForbiddenException(
                    "无权访问：需要 " + Arrays.toString(allowed) + "，当前为 " + user.permission());
        }
        return true;
    }
}
