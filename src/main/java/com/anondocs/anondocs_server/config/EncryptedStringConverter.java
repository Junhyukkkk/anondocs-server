package com.anondocs.anondocs_server.config;

import com.anondocs.anondocs_server.service.EncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Converter
@Component
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final EncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }

        return encryptionService.encrypt(plainText);
    }

    @Override
    public String convertToEntityAttribute(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }

        String decrypted = encryptionService.decrypt(encryptedText);
        return decrypted;
    }
}