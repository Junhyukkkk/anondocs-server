package com.anondocs.anondocs_server.service;

import com.anondocs.anondocs_server.domain.diary.Diary;
import com.anondocs.anondocs_server.domain.diary.DiaryVisibility;
import com.anondocs.anondocs_server.domain.user.User;
import com.anondocs.anondocs_server.domain.user.UserStatus;
import com.anondocs.anondocs_server.repository.DiaryRepository;
import com.anondocs.anondocs_server.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@TestPropertySource(properties = {
        "app.encryption.key=O430dNR1vCfxDURzbwXjUp0+gqMDiKymtc0PDe1ZzGo="
})
class DiaryEncryptionIntegrationTest {

    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .passwordHash("hashed")
                .nickname("테스터")
                .userStatus(UserStatus.ACTIVE)
                .build();
        userRepository.save(testUser);
    }

    @Test
    void DB에_저장된_일기는_암호화되어_있음() {
        // given
        String plainTitle = "테스트 제목";
        String plainContent = "오늘은 정말 힘든 하루였다...";

        Diary diary = Diary.makeDiary(plainTitle, plainContent,
                DiaryVisibility.PRIVATE, testUser);

        // when
        Diary saved = diaryRepository.save(diary);
        diaryRepository.flush();  // 즉시 DB 반영

        // then - JPA로 조회하면 복호화된 평문
        Diary found = diaryRepository.findById(saved.getId()).get();
        assertThat(found.getTitle()).isEqualTo(plainTitle);
        assertThat(found.getContent()).isEqualTo(plainContent);

        // then - 직접 SQL로 조회하면 암호화된 상태
        String encryptedTitle = jdbcTemplate.queryForObject(
                "SELECT title FROM diary WHERE id = ?",
                String.class,
                saved.getId()
        );
        String encryptedContent = jdbcTemplate.queryForObject(
                "SELECT content FROM diary WHERE id = ?",
                String.class,
                saved.getId()
        );

        // 암호화된 데이터는 평문과 달라야 함
        assertThat(encryptedTitle).isNotEqualTo(plainTitle);
        assertThat(encryptedContent).isNotEqualTo(plainContent);

        System.out.println("=".repeat(60));
        System.out.println("✅ 암호화 검증 성공!");
        System.out.println("평문 제목: " + plainTitle);
        System.out.println("DB 저장: " + encryptedTitle.substring(0, 50) + "...");
        System.out.println();
        System.out.println("평문 내용: " + plainContent);
        System.out.println("DB 저장: " + encryptedContent.substring(0, 50) + "...");
        System.out.println("=".repeat(60));
    }

    @Test
    void 일기_수정_후에도_암호화_유지() {
        // given
        Diary diary = Diary.makeDiary("원본 제목", "원본 내용",
                DiaryVisibility.PRIVATE, testUser);
        Diary saved = diaryRepository.save(diary);

        // when
        saved.updateDiary("수정된 제목", "수정된 내용", DiaryVisibility.PRIVATE);
        diaryRepository.flush();

        // then
        Diary updated = diaryRepository.findById(saved.getId()).get();
        assertThat(updated.getTitle()).isEqualTo("수정된 제목");
        assertThat(updated.getContent()).isEqualTo("수정된 내용");

        // DB에는 암호화되어 있음
        String encryptedContent = jdbcTemplate.queryForObject(
                "SELECT content FROM diary WHERE id = ?",
                String.class,
                saved.getId()
        );
        assertThat(encryptedContent).isNotEqualTo("수정된 내용");
    }

    @Test
    void 여러_일기를_저장해도_각각_다르게_암호화() {
        // given
        String sameContent = "동일한 내용";

        Diary diary1 = Diary.makeDiary("일기1", sameContent,
                DiaryVisibility.PRIVATE, testUser);
        Diary diary2 = Diary.makeDiary("일기2", sameContent,
                DiaryVisibility.PRIVATE, testUser);

        // when
        Diary saved1 = diaryRepository.save(diary1);
        Diary saved2 = diaryRepository.save(diary2);
        diaryRepository.flush();

        // then - DB에 저장된 암호문은 서로 다름 (IV가 다르기 때문)
        String encrypted1 = jdbcTemplate.queryForObject(
                "SELECT content FROM diary WHERE id = ?",
                String.class,
                saved1.getId()
        );
        String encrypted2 = jdbcTemplate.queryForObject(
                "SELECT content FROM diary WHERE id = ?",
                String.class,
                saved2.getId()
        );

        assertThat(encrypted1).isNotEqualTo(encrypted2);

        // but 복호화하면 둘 다 같은 평문
        assertThat(saved1.getContent()).isEqualTo(sameContent);
        assertThat(saved2.getContent()).isEqualTo(sameContent);
    }
}