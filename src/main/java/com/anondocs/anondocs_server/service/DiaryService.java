package com.anondocs.anondocs_server.service;

import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.domain.diary.DiaryVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DiaryService {

    Diary createDiary(Long userId, String title, String content, DiaryVisibility diaryVisibility);

    Page<Diary> getMyDiaries(Long userId, Pageable pageable);

    Diary getMyDiary(Long userId, Long diaryId);

    Diary updateDiary(Long userId, Long diaryId, String title, String content, DiaryVisibility visibility);

    void deleteDiary(Long userId, Long diaryId);

    Page<Diary> getPublicFeed(Pageable pageable);

    Diary updateDiaryContentLww(Long userId, Long diaryId, String content);

    Diary updateDiaryContentWithVersion(Long userId, Long diaryId, String content, Long expectedVersion);

}
