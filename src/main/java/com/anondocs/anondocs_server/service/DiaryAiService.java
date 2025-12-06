package com.anondocs.anondocs_server.service;

import com.anondocs.anondocs_server.repository.DiaryAiResultRepository;
import com.anondocs.anondocs_server.domain.ai.DiaryAiResult;
import com.anondocs.anondocs_server.domain.ai.EmotionLabel;
import com.anondocs.anondocs_server.domain.diary.Diary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
@RequiredArgsConstructor
public class DiaryAiService{

    private final DiaryAiResultRepository diaryAiResultRepository;

    public DiaryAiResult analyzeAndSave(Diary diary) {

        String content = diary.getContent();
        String summaryShort = makeShortSummary(content);
        String keywords = null;
        String summaryLong = summaryShort;

        EmotionLabel emotionLabel = EmotionLabel.NEUTRAL;

        // 기존 결과가 있으면 업데이트, 없으면 새로 생성
        DiaryAiResult aiResult = diaryAiResultRepository.findByDiary(diary)
                .orElseGet(() -> DiaryAiResult.makeDiaryAiResult(diary, summaryLong, summaryShort, keywords, emotionLabel)); // orElseGet이 성능상 조금 더 유리함

        aiResult.updateAiResult(summaryShort, summaryLong, keywords, emotionLabel);

        return diaryAiResultRepository.save(aiResult);
    }

    private String makeShortSummary(String content) {
        if (content == null) {
            return "";
        }
        int maxLen = 80;
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "...";
    }
}
