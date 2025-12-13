package com.anondocs.anondocs_server.controller;

import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.dto.*;
import com.anondocs.anondocs_server.exception.DiaryVersionConflictException;
import com.anondocs.anondocs_server.service.DiaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 실시간 일기 생성/편집 컨트롤러 (WebSocket + STOMP)
 *
 * 모든 일기 쓰기 작업은 WebSocket을 통해서만 가능:
 * - 일기 생성: /app/diaries/create
 * - 일기 편집 (LWW): /app/diaries/{diaryId}/edit-lww (마지막 쓰기 승리, 버전 체크 없음)
 * - 일기 편집 (Version): /app/diaries/{diaryId}/edit (버전 기반 낙관적 락, 충돌 감지)
 *
 * LWW(Last Write Wins) 방식:
 * - 버전 체크 없이 마지막으로 도착한 수정이 항상 적용됨
 * - 간단한 시나리오에 적합
 *
 * Version 기반 낙관적 락 방식:
 * - @Version 어노테이션으로 동시성 충돌 감지
 * - 충돌 시 에러 전송, 클라이언트가 최신 버전으로 재시도
 * - 협업 시나리오에 적합
 *
 * 동작 방식:
 * 1. 클라이언트가 STOMP 메시지 전송
 * 2. 서비스 레이어에서 처리 (LWW 또는 Version 체크)
 * 3. 성공 시: /topic/diaries/{diaryId} 로 모든 구독자에게 브로드캐스트
 * 4. 실패 시: /queue/errors 로 해당 사용자에게만 에러 전송
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DiaryRealTimeController {

    private final DiaryService diaryService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 실시간 일기 생성
     *
     * @param message   일기 생성 정보 (title, content, visibility)
     * @param principal 인증된 사용자 정보
     */
    @MessageMapping("/diaries/create")
    public void createDiary(DiaryCreateMessageDto message, Principal principal) {
        try {
            // 1. 사용자 인증 정보 추출
            Authentication auth = (Authentication) principal;
            UserPrincipalDto userPrincipal = (UserPrincipalDto) auth.getPrincipal();
            Long userId = userPrincipal.getId();

            log.debug("WebSocket 일기 생성 요청 - 사용자: {}, 제목: {}", userId, message.getTitle());

            // 2. 일기 생성
            Diary diary = diaryService.createDiary(
                    userId,
                    message.getTitle(),
                    message.getContent(),
                    message.getVisibility()
            );

            // 3. 생성 성공: 해당 일기 토픽에 브로드캐스트
            DiaryCreateBroadcastMessageDto broadcast = DiaryCreateBroadcastMessageDto.builder()
                    .diaryId(diary.getId())
                    .title(diary.getTitle())
                    .content(diary.getContent())
                    .version(diary.getVersion())
                    .creatorUserId(userPrincipal.getId())
                    .creatorNickname(userPrincipal.getNickname())
                    .timestamp(System.currentTimeMillis())
                    .build();

            messagingTemplate.convertAndSend(
                    "/topic/diaries/" + diary.getId(),
                    broadcast
            );

            // 4. 생성자에게도 직접 알림 (일기 ID 전달)
            messagingTemplate.convertAndSendToUser(
                    userPrincipal.getEmail(),
                    "/queue/diary-created",
                    broadcast
            );

            log.debug("일기 생성 완료 - 일기 ID: {}, 버전: {}", diary.getId(), diary.getVersion());

        } catch (Exception e) {
            // 5. 생성 실패: 해당 사용자에게만 에러 전송
            Authentication auth = (Authentication) principal;
            UserPrincipalDto userPrincipal = (UserPrincipalDto) auth.getPrincipal();

            log.error("일기 생성 실패 - 사용자: {}", userPrincipal.getEmail(), e);

            DiaryEditErrorMessageDto error = DiaryEditErrorMessageDto.builder()
                    .diaryId(null)
                    .code("CREATE_FAILED")
                    .message("일기 생성에 실패했습니다: " + e.getMessage())
                    .build();

            messagingTemplate.convertAndSendToUser(
                    userPrincipal.getEmail(),
                    "/queue/errors",
                    error
            );
        }
    }

    /**
     * 실시간 일기 편집 (버전 기반 동시성 제어)
     *
     * @param diaryId   편집할 일기 ID
     * @param message   편집 내용 (content, version 포함)
     * @param principal 인증된 사용자 정보
     */
    @MessageMapping("/diaries/{diaryId}/edit")
    public void editDiary(@DestinationVariable Long diaryId,
                          DiaryEditMessageDto message,
                          Principal principal) {

        try {
            // 1. 사용자 인증 정보 추출
            Authentication auth = (Authentication) principal;
            UserPrincipalDto userPrincipal = (UserPrincipalDto) auth.getPrincipal();
            Long userId = userPrincipal.getId();

            log.debug("WebSocket 편집 요청 - 사용자: {}, 일기: {}, 버전: {}",
                     userId, diaryId, message.getVersion());

            // 2. 버전 기반 업데이트 (낙관적 락)
            Diary diary = diaryService.updateDiaryContentWithVersion(
                    userId,
                    diaryId,
                    message.getContent(),
                    message.getVersion()
            );

            // 3. 성공 시: 모든 구독자에게 브로드캐스트
            DiaryEditBroadcastMessageDto broadcast = DiaryEditBroadcastMessageDto.builder()
                    .diaryId(diary.getId())
                    .content(diary.getContent())
                    .version(diary.getVersion())
                    .editorUserId(userPrincipal.getId())
                    .editorNickname(userPrincipal.getNickname())
                    .timestamp(System.currentTimeMillis())
                    .build();

            messagingTemplate.convertAndSend(
                    "/topic/diaries/" + diaryId,
                    broadcast
            );

            log.debug("브로드캐스트 성공 - 일기: {}, 새 버전: {}", diaryId, diary.getVersion());

        } catch (DiaryVersionConflictException e) {
            // 4. 버전 충돌 시: 토픽으로 브로드캐스트 (같은 사용자의 여러 세션도 모두 받을 수 있도록)
            Authentication auth = (Authentication) principal;
            UserPrincipalDto userPrincipal = (UserPrincipalDto) auth.getPrincipal();

            log.warn("버전 충돌 - 일기: {}, 사용자: {}, 요청 버전: {}, 현재 버전: {}",
                    diaryId, userPrincipal.getEmail(), message.getVersion(), e.getCurrentVersion());

            DiaryEditErrorMessageDto error = DiaryEditErrorMessageDto.builder()
                    .diaryId(diaryId)
                    .code("VERSION_CONFLICT")
                    .message("다른 사용자가 먼저 수정했습니다. 최신 내용을 불러온 후 다시 시도하세요.")
                    .currentVersion(e.getCurrentVersion())
                    .build();

            String errorTopic = "/topic/diaries/" + diaryId + "/errors";
            log.info("버전 충돌 에러 브로드캐스트 - 토픽: {}, 현재버전: {}", errorTopic, e.getCurrentVersion());

            messagingTemplate.convertAndSend(errorTopic, error);

        } catch (Exception e) {
            // 5. 기타 예외: 토픽으로 브로드캐스트
            Authentication auth = (Authentication) principal;
            UserPrincipalDto userPrincipal = (UserPrincipalDto) auth.getPrincipal();

            log.error("일기 편집 실패 - 일기: {}, 사용자: {}", diaryId, userPrincipal.getEmail(), e);

            DiaryEditErrorMessageDto error = DiaryEditErrorMessageDto.builder()
                    .diaryId(diaryId)
                    .code("EDIT_FAILED")
                    .message("편집에 실패했습니다: " + e.getMessage())
                    .build();

            messagingTemplate.convertAndSend(
                    "/topic/diaries/" + diaryId + "/errors",
                    error
            );
        }
    }

    /**
     * 실시간 일기 편집 - LWW(Last Write Wins) 방식
     * 버전 체크 없이 마지막 쓰기가 항상 승리하는 방식
     *
     * @param diaryId   편집할 일기 ID
     * @param message   편집 내용 (content만 포함, version 불필요)
     * @param principal 인증된 사용자 정보
     */
    @MessageMapping("/diaries/{diaryId}/edit-lww")
    public void editDiaryLww(@DestinationVariable Long diaryId,
                             DiaryEditLwwMessageDto message,
                             Principal principal) {

        try {
            // 1. 사용자 인증 정보 추출
            Authentication auth = (Authentication) principal;
            UserPrincipalDto userPrincipal = (UserPrincipalDto) auth.getPrincipal();
            Long userId = userPrincipal.getId();

            log.debug("WebSocket LWW 편집 요청 - 사용자: {}, 일기: {}",
                    userId, diaryId);

            // 2. LWW 방식 업데이트 (버전 체크 없음)
            Diary diary = diaryService.updateDiaryContentLww(
                    userId,
                    diaryId,
                    message.getContent()
            );

            // 3. 성공 시: 모든 구독자에게 브로드캐스트
            DiaryEditBroadcastMessageDto broadcast = DiaryEditBroadcastMessageDto.builder()
                    .diaryId(diary.getId())
                    .content(diary.getContent())
                    .version(diary.getVersion())
                    .editorUserId(userPrincipal.getId())
                    .editorNickname(userPrincipal.getNickname())
                    .timestamp(System.currentTimeMillis())
                    .build();

            messagingTemplate.convertAndSend(
                    "/topic/diaries/" + diaryId,
                    broadcast
            );

            log.debug("LWW 브로드캐스트 성공 - 일기: {}, 버전: {}", diaryId, diary.getVersion());

        } catch (Exception e) {
            // 4. 실패 시: 토픽으로 브로드캐스트
            Authentication auth = (Authentication) principal;
            UserPrincipalDto userPrincipal = (UserPrincipalDto) auth.getPrincipal();

            log.error("LWW 일기 편집 실패 - 일기: {}, 사용자: {}", diaryId, userPrincipal.getEmail(), e);

            DiaryEditErrorMessageDto error = DiaryEditErrorMessageDto.builder()
                    .diaryId(diaryId)
                    .code("EDIT_FAILED")
                    .message("편집에 실패했습니다: " + e.getMessage())
                    .build();

            messagingTemplate.convertAndSend(
                    "/topic/diaries/" + diaryId + "/errors",
                    error
            );
        }
    }
}