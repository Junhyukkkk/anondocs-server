package com.anondocs.anondocs_server.exception;


import lombok.Getter;

@Getter
public class DiaryVersionConflictException extends RuntimeException {

    private final Long serverVersion;

    public DiaryVersionConflictException(String message, Long serverVersion) {
        super(message);
        this.serverVersion = serverVersion;
    }
}