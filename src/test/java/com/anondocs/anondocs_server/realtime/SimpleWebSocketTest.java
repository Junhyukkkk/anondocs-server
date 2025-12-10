package com.anondocs.anondocs_server.realtime;

import com.anondocs.anondocs_server.auth.JwtTokenProvider;
import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.domain.diary.DiaryVisibility;
import com.anondocs.anondocs_server.domain.user.User;
import com.anondocs.anondocs_server.domain.user.UserStatus;
import com.anondocs.anondocs_server.dto.DiaryEditMessageDto;
import com.anondocs.anondocs_server.repository.DiaryRepository;
import com.anondocs.anondocs_server.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application.properties")
class SimpleWebSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String wsUrl;

    @BeforeEach
    void setUp() {
        wsUrl = "http://localhost:" + port + "/ws";
        diaryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("WebSocket 연결 테스트")
    void testWebSocketConnection() throws Exception {
        // Given
        User user = User.builder()
                .email("test@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .nickname("TestUser")
                .userStatus(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        String token = jwtTokenProvider.generateAccessToken(user);

        // When
        WebSocketStompClient stompClient = createStompClient();
        StompSession session = connectWithAuth(stompClient, token);

        // Then
        assertThat(session.isConnected()).isTrue();

        session.disconnect();
        stompClient.stop();
    }

    @Test
    @DisplayName("메시지 송수신 테스트")
    void testMessageSendReceive() throws Exception {
        // Given
        User user = User.builder()
                .email("test@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .nickname("TestUser")
                .userStatus(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        Diary diary = Diary.makeDiary("제목", "초기내용", DiaryVisibility.PRIVATE, user);
        diaryRepository.save(diary);

        String token = jwtTokenProvider.generateAccessToken(user);

        WebSocketStompClient stompClient = createStompClient();
        StompSession session = connectWithAuth(stompClient, token);

        // When - 구독
        final String[] receivedContent = {null};
        session.subscribe("/topic/diaries/" + diary.getId(), new StompFrameHandler() {
            @Override
            public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedContent[0] = (String) payload;
            }
        });

        Thread.sleep(500);

        // 메시지 전송
        DiaryEditMessageDto message = new DiaryEditMessageDto();
        message.setDiaryId(diary.getId());
        message.setContent("테스트 내용");

        session.send("/app/diaries/" + diary.getId() + "/edit", message);

        // Then - 메시지 수신 대기 및 검증
        Thread.sleep(2000);

        assertThat(receivedContent[0]).isNotNull();

        session.disconnect();
        stompClient.stop();
    }

    // Helper Methods

    private WebSocketStompClient createStompClient() {
        // SockJS 클라이언트 생성 (서버가 SockJS를 사용하므로)
        SockJsClient sockJsClient = new SockJsClient(
            List.of(new WebSocketTransport(new StandardWebSocketClient()))
        );
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient;
    }

    private StompSession connectWithAuth(WebSocketStompClient stompClient, String token) throws Exception {
        // WebSocket 핸드셰이크용 HTTP 헤더 (비어있어도 됨, SecurityConfig에서 /ws/** 허용)
        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();

        // STOMP CONNECT 프레임에 포함될 헤더 (JWT 인증용)
        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.add("Authorization", "Bearer " + token);

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                // 연결 성공
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                                       StompHeaders headers, byte[] payload, Throwable exception) {
                // 예외 처리
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                // 전송 에러 처리
            }
        };

        return stompClient.connectAsync(wsUrl, httpHeaders, stompHeaders, sessionHandler)
                .get(10, TimeUnit.SECONDS);
    }
}

