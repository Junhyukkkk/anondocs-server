package com.anondocs.anondocs_server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DiaryEditMessageDto {

    private Long diaryId; // 안전하게 메시지 안에도 포함

    @NotBlank
    private String content;

    @NotNull
    private Long version;
}