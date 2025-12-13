package com.anondocs.anondocs_server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * LWW(Last Write Wins) 방식의 일기 편집 메시지 DTO
 * 버전 정보 없이 마지막 쓰기가 항상 승리하는 방식
 */
@Getter
@Setter
@NoArgsConstructor
public class DiaryEditLwwMessageDto {

    private Long diaryId; // 안전하게 메시지 안에도 포함

    @NotBlank
    private String content;
}

