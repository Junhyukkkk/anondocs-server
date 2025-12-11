package com.anondocs.anondocs_server.domain.diary;

import com.anondocs.anondocs_server.domain.BaseTimeEntity;
import com.anondocs.anondocs_server.domain.ai.DiaryAiResult;
import com.anondocs.anondocs_server.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Diary extends BaseTimeEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // 작성자 (외부 노출x)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 255)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DiaryVisibility visibility;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @OneToOne(mappedBy = "diary", fetch = FetchType.LAZY)
    private DiaryAiResult diaryAiResult;

    @Builder
    public Diary(String title, String content, DiaryVisibility diaryVisibility) {
        this.title = title;
        this.content = content;
        this.visibility = diaryVisibility;
    }

    public static Diary makeDiary(String title, String content, DiaryVisibility diaryVisibility, User user){
        Diary diary = Diary.builder()
                .title(title)
                .content(content)
                .diaryVisibility(diaryVisibility)
                .build();
        diary.setUser(user);
        return diary;
    }

    public void updateDiary(String title, String content, DiaryVisibility diaryVisibility){
        this.title = title;
        this.content = content;
        this.visibility = diaryVisibility;
    }

    public void setUser(User user) {
        this.user = user;
        user.addDiary(this);
    }

    public void setDiaryAiResult(DiaryAiResult diaryAiResult) {
        this.diaryAiResult = diaryAiResult;
    }

    public void changeContent(String newContent){
        this.content = newContent;
    }

    public void Delete() {
        this.deleted = true;
    }

    public void publishIfAnonymous() {
        if (this.visibility == DiaryVisibility.ANONYMOUS && this.publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }

}
