package com.anondocs.anondocs_server.repository;

import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.domain.diary.DiaryVisibility;
import com.anondocs.anondocs_server.domain.user.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

    // 내 일기 목록 조회 (삭제되지 않은 것만)
    Page<Diary> findByUserAndDeletedFalse(User user, Pageable pageable);

    // 익명 공개 피드용 (삭제되지 않고 ANONYMOUS인 것만)
    Page<Diary> findByVisibilityAndDeletedFalse(DiaryVisibility visibility, Pageable pageable);

}
