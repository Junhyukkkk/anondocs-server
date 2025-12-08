package com.anondocs.anondocs_server.controller;

import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.dto.DiaryEditBroadcastMessageDto;
import com.anondocs.anondocs_server.dto.DiaryEditMessageDto;
import com.anondocs.anondocs_server.dto.UserPrincipalDto;
import com.anondocs.anondocs_server.service.DiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class DiaryRealTimeController {

    private final DiaryService diaryService;
    private final SimpMessagingTemplate messagingTemplate;


    @MessageMapping("/diaries/{diaryId}/edit")
    public void editDiary(@DestinationVariable Long diaryId,
                          DiaryEditMessageDto message,
                          Principal principal) {

        Authentication auth = (Authentication) principal;
        UserPrincipalDto userPrincipal = (UserPrincipalDto) auth.getPrincipal();
        Long userId = userPrincipal.getId();

        Diary diary = diaryService.updateDiaryContentLww(
                userId,
                diaryId,
                message.getContent()
        );

        DiaryEditBroadcastMessageDto broadcast = DiaryEditBroadcastMessageDto.builder()
                .diaryId(diary.getId())
                .content(diary.getContent())
                .editorUserId(userPrincipal.getId())
                .editorNickname(userPrincipal.getNickname())
                .build();

        messagingTemplate.convertAndSend(
                "/topic/diaries/" + diaryId,
                broadcast
        );
    }
}