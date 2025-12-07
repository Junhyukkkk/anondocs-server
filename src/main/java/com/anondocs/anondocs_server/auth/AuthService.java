package com.anondocs.anondocs_server.auth;

import com.anondocs.anondocs_server.service.UserService;
import com.anondocs.anondocs_server.domain.user.User;
import com.anondocs.anondocs_server.dto.AuthLoginRequest;
import com.anondocs.anondocs_server.dto.AuthLoginResponse;
import com.anondocs.anondocs_server.dto.AuthSignupRequest;
import com.anondocs.anondocs_server.dto.AuthSignupResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthSignupResponse signup(AuthSignupRequest request) {
        // UserService에 회원가입 위임
        User user = userService.register(
                request.getEmail(),
                request.getPassword(),
                request.getNickname()
        );

        return AuthSignupResponse.from(user);
    }

    public AuthLoginResponse login(AuthLoginRequest request) {
        // 이메일로 유저 조회
        User user = userService.getByEmail(request.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("이메일 또는 비밀번호가 올바르지 않습니다."));

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // JWT 발급
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        return AuthLoginResponse.of(accessToken, user);
    }
}