package com.anondocs.anondocs_server.dto;

import com.anondocs.anondocs_server.domain.diary.Diary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 피드용 일기 응답 DTO (익명 공개 일기)
 * 작성자 정보는 포함하지 않음
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryFeedItemResponse {

    private Long id;
    private String title;
    private String content;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    public static DiaryFeedItemResponse from(Diary diary) {
        return DiaryFeedItemResponse.builder()
                .id(diary.getId())
                .title(diary.getTitle())
                .content(diary.getContent())
                .publishedAt(diary.getPublishedAt())
                .createdAt(diary.getCreatedAt())
                .build();
    }
}

