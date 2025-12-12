package com.anondocs.anondocs_server.controller;

import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.dto.DiaryFeedItemResponse;
import com.anondocs.anondocs_server.dto.DiaryResponse;
import com.anondocs.anondocs_server.dto.UserPrincipalDto;
import com.anondocs.anondocs_server.service.DiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 일기 조회/삭제 컨트롤러 (REST API)
 *
 * 역할 분리:
 * - DiaryController: 일기 조회, 삭제 (읽기 작업)
 * - DiaryRealTimeController: 일기 생성, 편집 (모든 쓰기 작업은 WebSocket으로)
 */
@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
@Validated
public class DiaryController {

    private final DiaryService diaryService;

    // 1. 내 일기 목록 조회 (페이징)
    @GetMapping("/me")
    public ResponseEntity<Page<DiaryResponse>> getMyDiaries(
            @AuthenticationPrincipal UserPrincipalDto principal,
            Pageable pageable
    ) {
        Long userId = requireAuthenticated(principal);

        Page<Diary> diaries = diaryService.getMyDiaries(userId, pageable);
        Page<DiaryResponse> body = diaries.map(DiaryResponse::from);

        return ResponseEntity.ok(body);
    }

    // 2. 내 일기 단건 조회
    @GetMapping("/{diaryId}")
    public ResponseEntity<DiaryResponse> getMyDiary(
            @AuthenticationPrincipal UserPrincipalDto principal,
            @PathVariable Long diaryId
    ) {
        Long userId = requireAuthenticated(principal);

        Diary diary = diaryService.getMyDiary(userId, diaryId);
        return ResponseEntity.ok(DiaryResponse.from(diary));
    }

    // 3. 내 일기 삭제 (소프트 삭제)
    @DeleteMapping("/{diaryId}")
    public ResponseEntity<Void> deleteDiary(
            @AuthenticationPrincipal UserPrincipalDto principal,
            @PathVariable Long diaryId
    ) {
        Long userId = requireAuthenticated(principal);

        diaryService.deleteDiary(userId, diaryId);
        return ResponseEntity.noContent().build();
    }

    // 4. 공용 피드 (익명 공개 일기 목록)
    @GetMapping("/feed")
    public ResponseEntity<Page<DiaryFeedItemResponse>> getPublicFeed(Pageable pageable) {
        Page<Diary> feed = diaryService.getPublicFeed(pageable);
        Page<DiaryFeedItemResponse> body = feed.map(DiaryFeedItemResponse::from);
        return ResponseEntity.ok(body);
    }

    private Long requireAuthenticated(UserPrincipalDto principal) {
        if (principal == null) {
            throw new AccessDeniedException("인증이 필요합니다.");
        }
        return principal.getId();
    }

}