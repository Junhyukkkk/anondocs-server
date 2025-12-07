package com.anondocs.anondocs_server.auth;

import com.anondocs.anondocs_server.domain.user.User;
import com.anondocs.anondocs_server.dto.UserPrincipalDto;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret-key:change-this-secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration-seconds:3600}") // 기본 1시간
    private long accessTokenExpirationSeconds;

    // === 액세스 토큰 생성 ===
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenExpirationSeconds);

        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))       // sub = userId
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .claim("email", user.getEmail())
                .claim("nickname", user.getNickname())
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    // === 토큰에서 Authentication 만들기 ===
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);

        Long userId = Long.parseLong(claims.getSubject());
        String email = claims.get("email", String.class);
        String nickname = claims.get("nickname", String.class);

        UserPrincipalDto principal = new UserPrincipalDto(userId, email, nickname);

        return new UsernamePasswordAuthenticationToken(
                principal,
                token,
                Collections.emptyList() // 아직 권한(Role) 안 쓰니까 빈 리스트
        );
    }

    // 토큰 유효한지 검증
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}