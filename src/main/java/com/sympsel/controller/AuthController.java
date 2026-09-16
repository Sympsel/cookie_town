package com.sympsel.controller;

import com.sympsel.dto.LoginRequest;
import com.sympsel.dto.LoginResponse;
import com.sympsel.dto.UserResponse;
import com.sympsel.entitys.User;
import com.sympsel.security.JwtService;
import com.sympsel.security.UnauthorizedException;
import com.sympsel.security.UserContext;
import com.sympsel.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /**
     * 登录：校验用户名+密码，成功则签发 JWT。凭据错误统一返回 401，不区分"用户不存在"与"密码错误"。
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        User user = userService.findByName(request.name())
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));
        if (!userService.verifyPassword(request.password(), user)) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", jwtService.getExpiration(), UserResponse.from(user)));
    }

    /**
     * 获取当前登录用户信息（依赖请求携带的有效 token）。
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        String uuid = UserContext.currentUuid();
        return userService.findByUuid(uuid)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.notFound().build());
    }
}
