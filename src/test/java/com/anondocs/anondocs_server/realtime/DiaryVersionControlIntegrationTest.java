package com.anondocs.anondocs_server.realtime;

import com.anondocs.anondocs_server.auth.JwtTokenProvider;
import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.domain.diary.DiaryVisibility;
import com.anondocs.anondocs_server.domain.user.User;
import com.anondocs.anondocs_server.domain.user.UserStatus;
import com.anondocs.anondocs_server.dto.DiaryEditBroadcastMessageDto;
import com.anondocs.anondocs_server.dto.DiaryEditErrorMessageDto;
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

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 버전 기반 낙관적 락(Optimistic Locking)을 사용한 실시간 일기 편집 테스트
 *
 * @Version 어노테이션을 사용하여 동시성 충돌을 감지하고 제어하는 방식 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application.properties")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DiaryVersionControlIntegrationTest {

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
                "공유 일기 (Version Control)",
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
    @DisplayName("버전 기반 편집 - 단일 사용자 정상 수정")
    void testVersionBasedSingleUserEdit() throws Exception {
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

        Thread.sleep(500);

        // When - 올바른 버전으로 수정
        Long currentVersion = sharedDiary.getVersion();
        DiaryEditMessageDto editMessage = new DiaryEditMessageDto();
        editMessage.setDiaryId(sharedDiary.getId());
        editMessage.setContent("버전 기반 수정");
        editMessage.setVersion(currentVersion);

        session.send("/app/diaries/" + sharedDiary.getId() + "/edit", editMessage);

        // Then - 브로드캐스트 메시지 수신 확인
        DiaryEditBroadcastMessageDto received = messageQueue.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getContent()).isEqualTo("버전 기반 수정");
        assertThat(received.getVersion()).isEqualTo(currentVersion + 1); // 버전 증가 확인

        // DB 확인
        Diary updatedDiary = diaryRepository.findById(sharedDiary.getId()).orElseThrow();
        assertThat(updatedDiary.getContent()).isEqualTo("버전 기반 수정");
        assertThat(updatedDiary.getVersion()).isEqualTo(currentVersion + 1);

        session.disconnect();
        stompClient.stop();
    }

    @Test
    @DisplayName("버전 기반 편집 - 버전 충돌 감지 (낙관적 락)")
    void testVersionConflictDetection() throws Exception {
        // Given - 같은 사용자가 두 개의 다른 세션(브라우저)에서 접속한 상황
        BlockingQueue<DiaryEditBroadcastMessageDto> broadcastQueue = new LinkedBlockingQueue<>();
        BlockingQueue<DiaryEditErrorMessageDto> errorQueue = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient1 = createStompClient();
        WebSocketStompClient stompClient2 = createStompClient();

        // 같은 사용자(user1)로 두 세션 연결
        StompSession session1 = connectWithAuth(stompClient1, accessToken1);
        StompSession session2 = connectWithAuth(stompClient2, accessToken1);  // ← accessToken1 사용

        // 두 세션 모두 브로드캐스트 구독
        StompFrameHandler broadcastHandler = new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DiaryEditBroadcastMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                broadcastQueue.offer((DiaryEditBroadcastMessageDto) payload);
            }
        };

        session1.subscribe("/topic/diaries/" + sharedDiary.getId(), broadcastHandler);
        session2.subscribe("/topic/diaries/" + sharedDiary.getId(), broadcastHandler);

        // 세션2는 에러 메시지도 구독 (토픽으로 변경)
        session2.subscribe("/topic/diaries/" + sharedDiary.getId() + "/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DiaryEditErrorMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorQueue.offer((DiaryEditErrorMessageDto) payload);
            }
        });

        Thread.sleep(500);

        Long initialVersion = sharedDiary.getVersion();

        // When - 세션1이 먼저 수정 (성공)
        DiaryEditMessageDto edit1 = new DiaryEditMessageDto();
        edit1.setDiaryId(sharedDiary.getId());
        edit1.setContent("세션1의 수정");
        edit1.setVersion(initialVersion);

        session1.send("/app/diaries/" + sharedDiary.getId() + "/edit", edit1);
        Thread.sleep(200); // 세션1의 수정이 완료될 때까지 대기

        // 세션2가 같은 버전으로 수정 시도 (실패 예상)
        DiaryEditMessageDto edit2 = new DiaryEditMessageDto();
        edit2.setDiaryId(sharedDiary.getId());
        edit2.setContent("세션2의 수정 (실패 예상)");
        edit2.setVersion(initialVersion); // 같은 버전으로 시도

        session2.send("/app/diaries/" + sharedDiary.getId() + "/edit", edit2);

        // Then - 세션1의 브로드캐스트만 성공
        DiaryEditBroadcastMessageDto broadcast1 = broadcastQueue.poll(5, TimeUnit.SECONDS);
        assertThat(broadcast1).isNotNull();
        assertThat(broadcast1.getContent()).isEqualTo("세션1의 수정");
        assertThat(broadcast1.getVersion()).isEqualTo(initialVersion + 1);

        // 세션2는 에러 메시지를 받음
        DiaryEditErrorMessageDto error = errorQueue.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT");
        assertThat(error.getCurrentVersion()).isEqualTo(initialVersion + 1);

        // DB에는 세션1의 수정만 반영됨
        Diary finalDiary = diaryRepository.findById(sharedDiary.getId()).orElseThrow();
        assertThat(finalDiary.getContent()).isEqualTo("세션1의 수정");
        assertThat(finalDiary.getVersion()).isEqualTo(initialVersion + 1);

        session1.disconnect();
        session2.disconnect();
        stompClient1.stop();
        stompClient2.stop();
    }

    @Test
    @DisplayName("버전 기반 편집 - 순차적 수정 시 버전 증가 확인")
    void testSequentialVersionIncrement() throws Exception {
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

        Thread.sleep(500);

        // When - 여러 번 순차적으로 수정 (올바른 버전 사용)
        Long currentVersion = sharedDiary.getVersion();
        String[] edits = {
                "첫 번째 수정",
                "두 번째 수정",
                "세 번째 수정"
        };

        for (int i = 0; i < edits.length; i++) {
            DiaryEditMessageDto editMessage = new DiaryEditMessageDto();
            editMessage.setDiaryId(sharedDiary.getId());
            editMessage.setContent(edits[i]);
            editMessage.setVersion(currentVersion + i);

            session.send("/app/diaries/" + sharedDiary.getId() + "/edit", editMessage);

            // 브로드캐스트 수신 확인
            DiaryEditBroadcastMessageDto received = messageQueue.poll(5, TimeUnit.SECONDS);
            assertThat(received).isNotNull();
            assertThat(received.getContent()).isEqualTo(edits[i]);
            assertThat(received.getVersion()).isEqualTo(currentVersion + i + 1);
        }

        // Then - 최종 버전 확인
        Diary finalDiary = diaryRepository.findById(sharedDiary.getId()).orElseThrow();
        assertThat(finalDiary.getContent()).isEqualTo("세 번째 수정");
        assertThat(finalDiary.getVersion()).isEqualTo(currentVersion + 3);

        session.disconnect();
        stompClient.stop();
    }

    @Test
    @DisplayName("버전 기반 편집 - 잘못된 버전으로 수정 시도")
    void testEditWithInvalidVersion() throws Exception {
        // Given
        BlockingQueue<DiaryEditErrorMessageDto> errorQueue = new LinkedBlockingQueue<>();
        WebSocketStompClient stompClient = createStompClient();
        StompSession session = connectWithAuth(stompClient, accessToken1);

        StompFrameHandler errorHandler = new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DiaryEditErrorMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorQueue.offer((DiaryEditErrorMessageDto) payload);
            }
        };

        StompSession.Subscription subscription = session.subscribe("/topic/diaries/" + sharedDiary.getId() + "/errors", errorHandler);

        Thread.sleep(1000); // 구독 완료 대기 시간 늘림

        // When - 존재하지 않는 버전으로 수정 시도
        Long wrongVersion = 999L;
        DiaryEditMessageDto editMessage = new DiaryEditMessageDto();
        editMessage.setDiaryId(sharedDiary.getId());
        editMessage.setContent("잘못된 버전 수정 시도");
        editMessage.setVersion(wrongVersion);

        session.send("/app/diaries/" + sharedDiary.getId() + "/edit", editMessage);

        // Then - 에러 메시지 수신
        DiaryEditErrorMessageDto error = errorQueue.poll(10, TimeUnit.SECONDS);



        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT");
        assertThat(error.getCurrentVersion()).isEqualTo(sharedDiary.getVersion());

        // DB는 변경되지 않음
        Diary unchangedDiary = diaryRepository.findById(sharedDiary.getId()).orElseThrow();
        assertThat(unchangedDiary.getContent()).isEqualTo("초기 내용");

        session.disconnect();
        stompClient.stop();
    }

    @Test
    @DisplayName("버전 기반 편집 - 재시도 시나리오 (에러 후 최신 버전으로 재시도)")
    void testRetryWithLatestVersion() throws Exception {
        // Given
        BlockingQueue<DiaryEditBroadcastMessageDto> broadcastQueue = new LinkedBlockingQueue<>();
        BlockingQueue<DiaryEditErrorMessageDto> errorQueue = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = createStompClient();
        StompSession session = connectWithAuth(stompClient, accessToken1);

        session.subscribe("/topic/diaries/" + sharedDiary.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DiaryEditBroadcastMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                broadcastQueue.offer((DiaryEditBroadcastMessageDto) payload);
            }
        });

        session.subscribe("/topic/diaries/" + sharedDiary.getId() + "/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DiaryEditErrorMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorQueue.offer((DiaryEditErrorMessageDto) payload);
            }
        });

        Thread.sleep(500);

        Long initialVersion = sharedDiary.getVersion();

        // When - 1차: 올바른 버전으로 수정 (성공)
        DiaryEditMessageDto edit1 = new DiaryEditMessageDto();
        edit1.setDiaryId(sharedDiary.getId());
        edit1.setContent("첫 번째 수정");
        edit1.setVersion(initialVersion);

        session.send("/app/diaries/" + sharedDiary.getId() + "/edit", edit1);

        DiaryEditBroadcastMessageDto broadcast1 = broadcastQueue.poll(5, TimeUnit.SECONDS);
        assertThat(broadcast1).isNotNull();
        assertThat(broadcast1.getVersion()).isEqualTo(initialVersion + 1);

        // 2차: 잘못된 버전으로 수정 시도 (실패)
        DiaryEditMessageDto edit2 = new DiaryEditMessageDto();
        edit2.setDiaryId(sharedDiary.getId());
        edit2.setContent("두 번째 수정 - 실패");
        edit2.setVersion(initialVersion); // 이전 버전 사용

        session.send("/app/diaries/" + sharedDiary.getId() + "/edit", edit2);

        DiaryEditErrorMessageDto error = errorQueue.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT");
        Long latestVersion = error.getCurrentVersion();
        assertThat(latestVersion).isEqualTo(initialVersion + 1);

        // 3차: 최신 버전으로 재시도 (성공)
        DiaryEditMessageDto edit3 = new DiaryEditMessageDto();
        edit3.setDiaryId(sharedDiary.getId());
        edit3.setContent("두 번째 수정 - 성공");
        edit3.setVersion(latestVersion);

        session.send("/app/diaries/" + sharedDiary.getId() + "/edit", edit3);

        DiaryEditBroadcastMessageDto broadcast2 = broadcastQueue.poll(5, TimeUnit.SECONDS);
        assertThat(broadcast2).isNotNull();
        assertThat(broadcast2.getContent()).isEqualTo("두 번째 수정 - 성공");
        assertThat(broadcast2.getVersion()).isEqualTo(initialVersion + 2);

        // Then - 최종 상태 확인
        Diary finalDiary = diaryRepository.findById(sharedDiary.getId()).orElseThrow();
        assertThat(finalDiary.getContent()).isEqualTo("두 번째 수정 - 성공");
        assertThat(finalDiary.getVersion()).isEqualTo(initialVersion + 2);

        session.disconnect();
        stompClient.stop();
    }


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

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {};

        return stompClient.connectAsync(wsUrl, httpHeaders, stompHeaders, sessionHandler)
                .get(10, TimeUnit.SECONDS);
    }
}

