package com.anondocs.anondocs_server.controller;

import com.anondocs.anondocs_server.auth.AuthService;
import com.anondocs.anondocs_server.dto.AuthLoginRequest;
import com.anondocs.anondocs_server.dto.AuthLoginResponse;
import com.anondocs.anondocs_server.dto.AuthSignupRequest;
import com.anondocs.anondocs_server.dto.AuthSignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입
     * - 요청: email, password, nickname
     * - 응답: userId, email, nickname
     */
    @PostMapping("/signup")
    public ResponseEntity<AuthSignupResponse> signup(@Valid @RequestBody AuthSignupRequest request) {
        AuthSignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 로그인
     * - 요청: email, password
     * - 응답: accessToken + 유저 기본 정보
     */
    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        AuthLoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

}