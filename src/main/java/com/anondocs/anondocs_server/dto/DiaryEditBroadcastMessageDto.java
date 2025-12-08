package com.anondocs.anondocs_server.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DiaryEditBroadcastMessageDto {

    private Long diaryId;
    private String content;
    private Long editorUserId;
    private String editorNickname;
}
