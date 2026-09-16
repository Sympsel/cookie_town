package com.sympsel.security;

import com.sympsel.entitys.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析服务。使用 HS256 对称签名，密钥来自配置项 jwt.secret（环境变量 JWT_SECRET 注入）。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiration;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration}") long expiration) {
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT 密钥未配置或长度不足：请设置环境变量 JWT_SECRET（至少 32 字节 / 256 位）用于 HS256 签名");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
    }

    /**
     * 为用户签发 token，subject 存 uuid，附带 name 和 permission 声明，避免过滤器再查库。
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(user.getUuid())
                .claim("name", user.getName())
                .claim("permission", user.getPermission().name())
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签 token，返回 Claims；token 无效/过期会抛出 JJWT 的运行时异常。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpiration() {
        return expiration;
    }
}
