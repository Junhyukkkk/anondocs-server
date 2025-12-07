package com.anondocs.anondocs_server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipalDto {

    private final Long id;
    private final String email;
    private final String nickname;
}