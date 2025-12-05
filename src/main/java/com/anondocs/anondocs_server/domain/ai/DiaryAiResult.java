package com.anondocs.anondocs_server.domain.ai;

import com.anondocs.anondocs_server.domain.BaseTimeEntity;
import com.anondocs.anondocs_server.domain.diary.Diary;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class DiaryAiResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 일기 1 : 1 AI 결과
    @Setter
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diary_id", nullable = false, unique = true)
    private Diary diary;

    @Column(name = "summary_short", length = 500)
    private String summaryShort;

    @Lob
    @Column(name = "summary_long")
    private String summaryLong;

    @Enumerated(EnumType.STRING)
    @Column(name = "emotion_label", length = 30)
    private EmotionLabel emotionLabel;


    @Lob
    @Column(name = "keywords")
    private String keywords;

    @Builder
    public DiaryAiResult(Diary diary, String summaryLong, String summaryShort, String keywords, EmotionLabel emotionLabel) {
        this.setDiary(diary);
        this.summaryLong = summaryLong;
        this.summaryShort = summaryShort;
        this.keywords = keywords;
        this.emotionLabel = emotionLabel;
    }

    public static DiaryAiResult makeDiaryAiResult(Diary diary, String summaryLong, String summaryShort, String keywords, EmotionLabel emotionLabel){
        DiaryAiResult diaryAiResult = DiaryAiResult.builder()
                .diary(diary)
                .summaryLong(summaryLong)
                .summaryShort(summaryShort)
                .keywords(keywords)
                .emotionLabel(emotionLabel)
                .build();

        return diaryAiResult;
    }


    public void updateAiResult(String summaryLong, String summaryShort, String keywords, EmotionLabel emotionLabel){
        this.summaryLong = summaryLong;
        this.summaryShort = summaryShort;
        this.keywords = keywords;
        this.emotionLabel = emotionLabel;
    }

}
