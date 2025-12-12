package com.anondocs.anondocs_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일기 생성 성공 시 브로드캐스트 메시지 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryCreateBroadcastMessageDto {

    private Long diaryId;
    private String title;
    private String content;
    private Long version;
    private Long creatorUserId;
    private String creatorNickname;
    private Long timestamp;
}

