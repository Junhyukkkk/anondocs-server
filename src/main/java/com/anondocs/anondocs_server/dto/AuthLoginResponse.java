package com.anondocs.anondocs_server.dto;

import com.anondocs.anondocs_server.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AuthLoginResponse {

    private String accessToken;

    private Long userId;
    private String email;
    private String nickname;

    public static AuthLoginResponse of(String accessToken, User user) {
        return AuthLoginResponse.builder()
                .accessToken(accessToken)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }
}