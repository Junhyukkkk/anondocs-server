package com.anondocs.anondocs_server.dto;

import com.anondocs.anondocs_server.domain.diary.DiaryVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WebSocket을 통한 일기 생성 메시지 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class DiaryCreateMessageDto {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    @NotNull(message = "공개 범위는 필수입니다.")
    private DiaryVisibility visibility;
}

