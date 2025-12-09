package com.anondocs.anondocs_server.auth;

import com.anondocs.anondocs_server.dto.AuthLoginRequest;
import com.anondocs.anondocs_server.dto.AuthSignupRequest;
import com.anondocs.anondocs_server.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Value("${jwt.secret-key:change-this-secret}")
    private String secretKey;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 성공 - 유저 정보 반환")
    void signup_success() throws Exception {
        // Given
        AuthSignupRequest request = new AuthSignupRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setNickname("테스터");

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.nickname").value("테스터"));

        // 실제로 DB에 저장되었는지 확인
        assertThat(userRepository.findByEmail("test@example.com")).isPresent();
    }

    @Test
    @DisplayName("회원가입 실패 - 중복 이메일")
    void signup_fail_duplicateEmail() throws Exception {
        // Given - 먼저 한 명 가입
        AuthSignupRequest request = new AuthSignupRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setNickname("테스터1");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // When & Then - 같은 이메일로 다시 가입 시도
        AuthSignupRequest duplicateRequest = new AuthSignupRequest();
        duplicateRequest.setEmail("test@example.com");
        duplicateRequest.setPassword("password456");
        duplicateRequest.setNickname("테스터2");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입 실패 - 유효성 검증 (비밀번호 짧음)")
    void signup_fail_validation() throws Exception {
        // Given
        AuthSignupRequest request = new AuthSignupRequest();
        request.setEmail("test@example.com");
        request.setPassword("123");  // 8자 미만
        request.setNickname("테스터");

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인 성공 - JWT 토큰 발급 및 검증")
    void login_success_andVerifyToken() throws Exception {
        // Given - 회원가입 먼저
        AuthSignupRequest signupRequest = new AuthSignupRequest();
        signupRequest.setEmail("test@example.com");
        signupRequest.setPassword("password123");
        signupRequest.setNickname("테스터");

        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andReturn();

        // 회원가입 응답에서 userId 추출
        String signupResponse = signupResult.getResponse().getContentAsString();
        JsonNode signupJson = objectMapper.readTree(signupResponse);
        Long userId = signupJson.get("userId").asLong();

        // When - 로그인
        AuthLoginRequest loginRequest = new AuthLoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.nickname").value("테스터"))
                .andReturn();

        // Then - 토큰 내부 검증
        String loginResponse = loginResult.getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.get("accessToken").asText();

        // JWT 토큰 파싱 및 검증
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(accessToken)
                .getBody();

        // subject는 userId (String 형태)
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(userId));

        // 커스텀 클레임 검증
        assertThat(claims.get("email", String.class)).isEqualTo("test@example.com");
        assertThat(claims.get("nickname", String.class)).isEqualTo("테스터");

        // 토큰 만료 시간 검증 (미래 시간이어야 함)
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration().getTime()).isGreaterThan(System.currentTimeMillis());

        // 발행 시간 검증
        assertThat(claims.getIssuedAt()).isNotNull();
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 이메일")
    void login_fail_userNotFound() throws Exception {
        // Given
        AuthLoginRequest loginRequest = new AuthLoginRequest();
        loginRequest.setEmail("notexist@example.com");
        loginRequest.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("로그인 실패 - 잘못된 비밀번호")
    void login_fail_wrongPassword() throws Exception {
        // Given - 회원가입
        AuthSignupRequest signupRequest = new AuthSignupRequest();
        signupRequest.setEmail("test@example.com");
        signupRequest.setPassword("password123");
        signupRequest.setNickname("테스터");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        // When - 잘못된 비밀번호로 로그인
        AuthLoginRequest loginRequest = new AuthLoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("wrongpassword");

        // Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("여러 사용자 회원가입 및 로그인 - 각각 다른 토큰 발급")
    void multipleUsers_differentTokens() throws Exception {
        // Given - 두 명의 사용자 회원가입
        AuthSignupRequest user1 = new AuthSignupRequest();
        user1.setEmail("user1@example.com");
        user1.setPassword("password123");
        user1.setNickname("유저1");

        AuthSignupRequest user2 = new AuthSignupRequest();
        user2.setEmail("user2@example.com");
        user2.setPassword("password456");
        user2.setNickname("유저2");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user1)));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user2)));

        // When - 각각 로그인
        AuthLoginRequest login1 = new AuthLoginRequest();
        login1.setEmail("user1@example.com");
        login1.setPassword("password123");

        AuthLoginRequest login2 = new AuthLoginRequest();
        login2.setEmail("user2@example.com");
        login2.setPassword("password456");

        MvcResult result1 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login1)))
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login2)))
                .andReturn();

        // Then - 토큰이 다르고, 각각의 정보가 올바른지 검증
        String token1 = objectMapper.readTree(result1.getResponse().getContentAsString())
                .get("accessToken").asText();
        String token2 = objectMapper.readTree(result2.getResponse().getContentAsString())
                .get("accessToken").asText();

        assertThat(token1).isNotEqualTo(token2);

        Claims claims1 = Jwts.parserBuilder()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token1)
                .getBody();

        Claims claims2 = Jwts.parserBuilder()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token2)
                .getBody();

        assertThat(claims1.get("email", String.class)).isEqualTo("user1@example.com");
        assertThat(claims1.get("nickname", String.class)).isEqualTo("유저1");

        assertThat(claims2.get("email", String.class)).isEqualTo("user2@example.com");
        assertThat(claims2.get("nickname", String.class)).isEqualTo("유저2");
    }

}

