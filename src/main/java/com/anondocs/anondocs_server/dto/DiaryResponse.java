package com.anondocs.anondocs_server.dto;

import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.domain.diary.DiaryVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 일기 응답 DTO (내 일기 조회용)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryResponse {

    private Long id;
    private String title;
    private String content;
    private DiaryVisibility visibility;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    public static DiaryResponse from(Diary diary) {
        return DiaryResponse.builder()
                .id(diary.getId())
                .title(diary.getTitle())
                .content(diary.getContent())
                .visibility(diary.getVisibility())
                .version(diary.getVersion())
                .createdAt(diary.getCreatedAt())
                .updatedAt(diary.getUpdatedAt())
                .publishedAt(diary.getPublishedAt())
                .build();
    }
}

