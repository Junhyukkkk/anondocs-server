package com.anondocs.anondocs_server.config;

import com.anondocs.anondocs_server.auth.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST API라서 CSRF 비활성화
                .csrf(csrf -> csrf.disable())

                // 세션을 쓰지 않고, JWT로만 인증 상태 관리
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 요청별 인가 규칙
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",        // 회원가입/로그인
                                "/health",             // 헬스 체크 등
                                "/actuator/health",
                                "/ws/**"               // WebSocket 엔드포인트 (핸드셰이크)
                        ).permitAll()
                        .anyRequest().authenticated() // 나머지는 전부 인증 필요
                )

                // UsernamePasswordAuthenticationFilter 전에 JWT 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}