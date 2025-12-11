package com.anondocs.anondocs_server.service;

import com.anondocs.anondocs_server.exception.DiaryVersionConflictException;
import com.anondocs.anondocs_server.repository.DiaryRepository;
import com.anondocs.anondocs_server.repository.UserRepository;
import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.domain.diary.DiaryVisibility;
import com.anondocs.anondocs_server.domain.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
@RequiredArgsConstructor
public class DiaryServiceImpl implements DiaryService{

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryAiService diaryAiService;

    public Diary createDiary(Long userId, String title, String content, DiaryVisibility diaryVisibility) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        Diary diary = Diary.makeDiary(title, content, diaryVisibility, user);

        diary.publishIfAnonymous();
        Diary saved = diaryRepository.save(diary);

        // 나중에 비동기 방식으로 전환?
        diaryAiService.analyzeAndSave(saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Diary> getMyDiaries(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().isUnsorted()
                        ? Sort.by(Sort.Direction.DESC, "createdAt")
                        : pageable.getSort()
        );

        return diaryRepository.findByUserAndDeletedFalse(user, sorted);
    }

    @Transactional(readOnly = true)
    public Diary getMyDiary(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다."));

        if (diary.getUser().getId() != userId) {
            throw new SecurityException("본인의 일기만 조회할 수 있습니다.");
        }

        if (diary.isDeleted()) {
            throw new EntityNotFoundException("삭제된 일기입니다.");
        }

        return diary;
    }

    public Diary updateDiary(Long userId, Long diaryId, String title, String content, DiaryVisibility visibility) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다."));

        if (diary.getUser().getId() != userId) {
            throw new SecurityException("본인의 일기만 조회할 수 있습니다.");
        }

        if (diary.isDeleted()) {
            throw new EntityNotFoundException("삭제된 일기입니다.");
        }

        diary.updateDiary(title, content, visibility);
        diary.publishIfAnonymous();

        diaryAiService.analyzeAndSave(diary);

        return diary;
    }

    public void deleteDiary(Long userId, Long diaryId) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다."));

        if (diary.getUser().getId() != userId) {
            throw new SecurityException("본인의 일기만 조회할 수 있습니다.");
        }

        diary.Delete();
    }

    @Transactional(readOnly = true)
    public Page<Diary> getPublicFeed(Pageable pageable) {
        // 기본 정렬: publishedAt DESC
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().isUnsorted()
                        ? Sort.by(Sort.Direction.DESC, "publishedAt")
                        : pageable.getSort()
        );

        return diaryRepository.findByVisibilityAndDeletedFalse(DiaryVisibility.ANONYMOUS, sorted);
    }

    public Diary updateDiaryContentLww(Long userId, Long diaryId, String content) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다."));

        if (!Long.valueOf(diary.getUser().getId()).equals(userId)) {
            throw new SecurityException("본인의 일기만 수정할 수 있습니다.");
        }

        diary.changeContent(content); // 엔티티 메서드
        return diary;
    }

    @Override
    public Diary updateDiaryContentWithVersion(Long userId, Long diaryId, String content, Long expectedVersion) {
        Diary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new EntityNotFoundException("일기를 찾을 수 없습니다."));

        if (!Long.valueOf(diary.getUser().getId()).equals(userId)) {
            throw new SecurityException("본인의 일기만 수정할 수 있습니다.");
        }

        // 서버 버전 vs 클라이언트 버전 비교
        Long serverVersion = diary.getVersion();
        if (!serverVersion.equals(expectedVersion)) {
            // 이미 다른 사용자가 수정해서 버전이 달라진 상태
            throw new DiaryVersionConflictException("일기가 다른 사용자에 의해 이미 수정되었습니다.", serverVersion);
        }

        diary.changeContent(content); // 수정
        // 트랜잭션 종료 시 JPA가 version을 +1 해줌 (@Version 덕분)

        return diary;
    }

}