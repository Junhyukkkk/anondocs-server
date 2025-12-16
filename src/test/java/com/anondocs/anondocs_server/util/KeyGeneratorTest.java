package com.anondocs.anondocs_server.util;

import com.anondocs.anondocs_server.service.EncryptionService;
import org.junit.jupiter.api.Test;

class KeyGeneratorTest {

    @Test
    void generateEncryptionKey() throws Exception {
        String key = EncryptionService.generateKey();
        System.out.println("=".repeat(60));
        System.out.println("생성된 AES-256 암호화 키:");
        System.out.println(key);
        System.out.println("app.encryption.key=" + key);
        System.out.println("=".repeat(60));
    }
}