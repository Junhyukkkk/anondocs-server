package com.anondocs.anondocs_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() throws Exception {
        // 테스트용 키 생성
        String testKey = EncryptionService.generateKey();
        encryptionService = new EncryptionService(testKey);
    }

    @Test
    void 평문_암호화_후_복호화하면_원본과_동일() {
        // given
        String original = "오늘은 정말 힘든 하루였다...";

        // when
        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);

        // then
        assertThat(encrypted).isNotEqualTo(original);  // 암호문은 평문과 다름
        assertThat(decrypted).isEqualTo(original);     // 복호화하면 원본과 동일
    }

    @Test
    void 같은_평문을_두_번_암호화하면_다른_암호문() {
        // given
        String original = "테스트 메시지";

        // when
        String encrypted1 = encryptionService.encrypt(original);
        String encrypted2 = encryptionService.encrypt(original);

        // then
        assertThat(encrypted1).isNotEqualTo(encrypted2);  // IV가 다르므로 암호문 다름

        // but 둘 다 복호화하면 같은 원본
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(original);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(original);
    }

    @Test
    void null값은_null로_반환() {
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.decrypt(null)).isNull();
    }

    @Test
    void 빈_문자열은_빈_문자열로_반환() {
        assertThat(encryptionService.encrypt("")).isEmpty();
        assertThat(encryptionService.decrypt("")).isEmpty();
    }

    @Test
    void 긴_텍스트_암호화_복호화() {
        // given
        String longText = "가".repeat(10000);  // 10,000자

        // when
        String encrypted = encryptionService.encrypt(longText);
        String decrypted = encryptionService.decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(longText);
    }

    @Test
    void 잘못된_암호문_복호화시_예외_발생() {
        // given
        String invalidEncrypted = "ThisIsNotValidEncryptedData";

        // when & then
        assertThatThrownBy(() -> encryptionService.decrypt(invalidEncrypted))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("복호화");
    }
}
