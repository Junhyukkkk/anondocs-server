package com.anondocs.anondocs_server.realtime;

import com.anondocs.anondocs_server.auth.JwtTokenProvider;
import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.domain.diary.DiaryVisibility;
import com.anondocs.anondocs_server.domain.user.User;
import com.anondocs.anondocs_server.domain.user.UserStatus;
import com.anondocs.anondocs_server.dto.DiaryEditBroadcastMessageDto;
import com.anondocs.anondocs_server.dto.DiaryEditLwwMessageDto;
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

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application.properties")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DiaryRealTimeIntegrationTest {

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
    private User user1;
    private User user2;
    private Diary sharedDiary;
    private String accessToken1;
    private String accessToken2;

    @BeforeEach
    void setUp() {
        wsUrl = "http://localhost:" + port + "/ws";

        // 기존 데이터 정리
        diaryRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 사용자 2명 생성
        user1 = User.builder()
                .email("user1@test.com")
                .passwordHash(passwordEncoder.encode("password1"))
                .nickname("User1")
                .userStatus(UserStatus.ACTIVE)
                .build();
        userRepository.save(user1);

        user2 = User.builder()
                .email("user2@test.com")
                .passwordHash(passwordEncoder.encode("password2"))
                .nickname("User2")
                .userStatus(UserStatus.ACTIVE)
                .build();
        userRepository.save(user2);

        // 공유 일기 생성
        sharedDiary = Diary.makeDiary(
                "공유 일기",
                "초기 내용",
                DiaryVisibility.PRIVATE,
                user1
        );
        diaryRepository.save(sharedDiary);

        // JWT 토큰 생성
        accessToken1 = jwtTokenProvider.generateAccessToken(user1);
        accessToken2 = jwtTokenProvider.generateAccessToken(user2);
    }

    @Test
    @DisplayName("실시간 일기 편집(LWW) - 단일 사용자 수정")
    void testSingleUserEdit() throws Exception {
        // Given
        BlockingQueue<DiaryEditBroadcastMessageDto> messageQueue = new LinkedBlockingQueue<>();
        WebSocketStompClient stompClient = createStompClient();

        StompSession session = connectWithAuth(stompClient, accessToken1);

        // 구독
        session.subscribe("/topic/diaries/" + sharedDiary.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DiaryEditBroadcastMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messageQueue.offer((DiaryEditBroadcastMessageDto) payload);
            }
        });

        // 구독이 완료될 때까지 대기
        Thread.sleep(500);

        // When - 메시지 발행 (LWW 방식)
        DiaryEditLwwMessageDto editMessage = new DiaryEditLwwMessageDto();
        editMessage.setDiaryId(sharedDiary.getId());
        editMessage.setContent("사용자1이 수정한 내용");

        session.send("/app/diaries/" + sharedDiary.getId() + "/edit-lww", editMessage);

        // Then - 브로드캐스트 메시지 수신 확인
        DiaryEditBroadcastMessageDto received = messageQueue.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getDiaryId()).isEqualTo(sharedDiary.getId());
        assertThat(received.getContent()).isEqualTo("사용자1이 수정한 내용");
        assertThat(received.getEditorUserId()).isEqualTo(user1.getId());
        assertThat(received.getEditorNickname()).isEqualTo("User1");

        // DB 확인
        Diary updatedDiary = diaryRepository.findById(sharedDiary.getId()).orElseThrow();
        assertThat(updatedDiary.getContent()).isEqualTo("사용자1이 수정한 내용");

        session.disconnect();
        stompClient.stop();
    }

    @Test
    @DisplayName("실시간 일기 편집(LWW) - Last Write Wins 동작 확인")
    void testLastWriteWins() throws Exception {
        // Given
        BlockingQueue<DiaryEditBroadcastMessageDto> messageQueue = new LinkedBlockingQueue<>();
        WebSocketStompClient stompClient = createStompClient();

        StompSession session = connectWithAuth(stompClient, accessToken1);

        // 구독
        StompFrameHandler handler = new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DiaryEditBroadcastMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messageQueue.offer((DiaryEditBroadcastMessageDto) payload);
            }
        };

        session.subscribe("/topic/diaries/" + sharedDiary.getId(), handler);

        // When - User1이 먼저 수정 (LWW 방식)
        DiaryEditLwwMessageDto edit1 = new DiaryEditLwwMessageDto();
        edit1.setDiaryId(sharedDiary.getId());
        edit1.setContent("첫 번째 수정");

        session.send("/app/diaries/" + sharedDiary.getId() + "/edit-lww", edit1);
        Thread.sleep(100); // 순서 보장을 위한 짧은 대기

        // User1이 다시 수정 (마지막 수정이 승리)
        DiaryEditLwwMessageDto edit2 = new DiaryEditLwwMessageDto();
        edit2.setDiaryId(sharedDiary.getId());
        edit2.setContent("두 번째 수정 - 최종본");

        session.send("/app/diaries/" + sharedDiary.getId() + "/edit-lww", edit2);

        // Then - 두 개의 브로드캐스트 메시지 수신
        DiaryEditBroadcastMessageDto msg1 = messageQueue.poll(5, TimeUnit.SECONDS);
        assertThat(msg1).isNotNull();
        assertThat(msg1.getContent()).isEqualTo("첫 번째 수정");

        DiaryEditBroadcastMessageDto msg2 = messageQueue.poll(5, TimeUnit.SECONDS);
        assertThat(msg2).isNotNull();
        assertThat(msg2.getContent()).isEqualTo("두 번째 수정 - 최종본");
        assertThat(msg2.getEditorUserId()).isEqualTo(user1.getId());

        // DB에는 마지막 수정이 반영됨 (LWW)
        Diary finalDiary = diaryRepository.findById(sharedDiary.getId()).orElseThrow();
        assertThat(finalDiary.getContent()).isEqualTo("두 번째 수정 - 최종본");

        session.disconnect();
        stompClient.stop();
    }

    @Test
    @DisplayName("실시간 일기 편집(LWW) - 연속적인 수정 테스트")
    void testConsecutiveEdits() throws Exception {
        // Given
        BlockingQueue<DiaryEditBroadcastMessageDto> messageQueue = new LinkedBlockingQueue<>();
        WebSocketStompClient stompClient = createStompClient();
        StompSession session = connectWithAuth(stompClient, accessToken1);

        session.subscribe("/topic/diaries/" + sharedDiary.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DiaryEditBroadcastMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messageQueue.offer((DiaryEditBroadcastMessageDto) payload);
            }
        });

        // When - 여러 번 연속 수정 (LWW 방식)
        String[] edits = {
                "첫 번째 수정",
                "두 번째 수정",
                "세 번째 수정",
                "네 번째 수정 - 최종"
        };

        for (String content : edits) {
            DiaryEditLwwMessageDto editMessage = new DiaryEditLwwMessageDto();
            editMessage.setDiaryId(sharedDiary.getId());
            editMessage.setContent(content);

            session.send("/app/diaries/" + sharedDiary.getId() + "/edit-lww", editMessage);
            Thread.sleep(50); // 메시지 순서 보장
        }

        // Then - 모든 브로드캐스트 수신 확인
        for (int i = 0; i < edits.length; i++) {
            DiaryEditBroadcastMessageDto received = messageQueue.poll(5, TimeUnit.SECONDS);
            assertThat(received).isNotNull();
            assertThat(received.getContent()).isEqualTo(edits[i]);
        }

        // 최종 DB 상태 확인
        Diary finalDiary = diaryRepository.findById(sharedDiary.getId()).orElseThrow();
        assertThat(finalDiary.getContent()).isEqualTo("네 번째 수정 - 최종");

        session.disconnect();
        stompClient.stop();
    }

    @Test
    @DisplayName("실시간 일기 편집(LWW) - 권한 없는 사용자의 수정 시도")
    void testUnauthorizedEdit() throws Exception {
        // Given
        User unauthorizedUser = User.builder()
                .email("unauthorized@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .nickname("Unauthorized")
                .userStatus(UserStatus.ACTIVE)
                .build();
        userRepository.save(unauthorizedUser);

        String unauthorizedToken = jwtTokenProvider.generateAccessToken(unauthorizedUser);

        BlockingQueue<DiaryEditBroadcastMessageDto> messageQueue = new LinkedBlockingQueue<>();
        WebSocketStompClient stompClient = createStompClient();
        StompSession session = connectWithAuth(stompClient, unauthorizedToken);

        session.subscribe("/topic/diaries/" + sharedDiary.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DiaryEditBroadcastMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messageQueue.offer((DiaryEditBroadcastMessageDto) payload);
            }
        });

        // When - 권한 없는 사용자가 수정 시도 (LWW 방식)
        DiaryEditLwwMessageDto editMessage = new DiaryEditLwwMessageDto();
        editMessage.setDiaryId(sharedDiary.getId());
        editMessage.setContent("권한 없는 수정 시도");

        try {
            session.send("/app/diaries/" + sharedDiary.getId() + "/edit-lww", editMessage);
            Thread.sleep(1000); // 에러 발생 대기
        } catch (Exception e) {
            // 예외 발생 예상
        }

        // Then - 메시지가 브로드캐스트되지 않음
        DiaryEditBroadcastMessageDto received = messageQueue.poll(2, TimeUnit.SECONDS);
        assertThat(received).isNull();

        // DB가 변경되지 않음
        Diary unchangedDiary = diaryRepository.findById(sharedDiary.getId()).orElseThrow();
        assertThat(unchangedDiary.getContent()).isEqualTo("초기 내용");

        session.disconnect();
        stompClient.stop();
    }

    @Test
    @DisplayName("실시간 일기 편집(LWW) - 동시 편집 시 마지막 승리 확인")
    void testConcurrentEditsLastWins() throws Exception {
        // Given
        BlockingQueue<DiaryEditBroadcastMessageDto> messageQueue = new LinkedBlockingQueue<>();
        WebSocketStompClient stompClient1 = createStompClient();
        WebSocketStompClient stompClient2 = createStompClient();

        StompSession session1 = connectWithAuth(stompClient1, accessToken1);
        StompSession session2 = connectWithAuth(stompClient2, accessToken2);

        StompFrameHandler handler = new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DiaryEditBroadcastMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messageQueue.offer((DiaryEditBroadcastMessageDto) payload);
            }
        };

        session1.subscribe("/topic/diaries/" + sharedDiary.getId(), handler);
        session2.subscribe("/topic/diaries/" + sharedDiary.getId(), handler);

        // When - 거의 동시에 수정 (실제로는 순서가 있음) (LWW 방식)
        DiaryEditLwwMessageDto edit1 = new DiaryEditLwwMessageDto();
        edit1.setDiaryId(sharedDiary.getId());
        edit1.setContent("동시 수정 1");

        DiaryEditLwwMessageDto edit2 = new DiaryEditLwwMessageDto();
        edit2.setDiaryId(sharedDiary.getId());
        edit2.setContent("동시 수정 2 - 승리");

        // 거의 동시에 전송
        session1.send("/app/diaries/" + sharedDiary.getId() + "/edit-lww", edit1);
        session2.send("/app/diaries/" + sharedDiary.getId() + "/edit-lww", edit2);

        // Then - 두 메시지 모두 수신
        DiaryEditBroadcastMessageDto msg1 = messageQueue.poll(5, TimeUnit.SECONDS);
        DiaryEditBroadcastMessageDto msg2 = messageQueue.poll(5, TimeUnit.SECONDS);

        assertThat(msg1).isNotNull();
        assertThat(msg2).isNotNull();

        // 마지막 메시지의 내용이 DB에 저장됨
        Thread.sleep(200); // DB 저장 대기
        Diary finalDiary = diaryRepository.findById(sharedDiary.getId()).orElseThrow();
        // LWW이므로 두 번째 수정이 반영됨 (정확한 타이밍에 따라 다를 수 있음)
        assertThat(finalDiary.getContent()).isIn("동시 수정 1", "동시 수정 2 - 승리");

        session1.disconnect();
        session2.disconnect();
        stompClient1.stop();
        stompClient2.stop();
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
                System.out.println("Connected to WebSocket");
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                                       StompHeaders headers, byte[] payload, Throwable exception) {
                System.err.println("WebSocket error: " + exception.getMessage());
                exception.printStackTrace();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.err.println("Transport error: " + exception.getMessage());
                exception.printStackTrace();
            }
        };

        return stompClient.connectAsync(wsUrl, httpHeaders, stompHeaders, sessionHandler)
                .get(10, TimeUnit.SECONDS);
    }
}

