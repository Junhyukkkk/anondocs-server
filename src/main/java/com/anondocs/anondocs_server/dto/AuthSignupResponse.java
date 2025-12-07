package com.anondocs.anondocs_server.dto;

import com.anondocs.anondocs_server.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AuthSignupResponse {

    private Long userId;
    private String email;
    private String nickname;

    public static AuthSignupResponse from(User user) {
        return AuthSignupResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }
}