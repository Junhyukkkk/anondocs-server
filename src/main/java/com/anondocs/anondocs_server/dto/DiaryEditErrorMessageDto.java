package com.anondocs.anondocs_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WebSocket 편집 실패 시 클라이언트에게 전송할 에러 메시지 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryEditErrorMessageDto {

    private Long diaryId;
    private String code;
    private String message;
    private Long currentVersion;  // 버전 충돌 시 현재 서버의 버전
}

