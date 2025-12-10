package com.anondocs.anondocs_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryEditBroadcastMessageDto {

    private Long diaryId;
    private String content;
    private Long editorUserId;
    private String editorNickname;
}
