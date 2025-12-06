package com.anondocs.anondocs_server.repository;


import com.anondocs.anondocs_server.domain.ai.DiaryAiResult;
import com.anondocs.anondocs_server.domain.diary.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiaryAiResultRepository extends JpaRepository<DiaryAiResult, Long> {

    Optional<DiaryAiResult> findByDiary(Diary diary);

}
