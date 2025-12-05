package com.anondocs.anondocs_server.domain.user;

import com.anondocs.anondocs_server.domain.BaseTimeEntity;
import com.anondocs.anondocs_server.domain.diary.Diary;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue
    private long id;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus userStatus;

    @OneToMany(mappedBy = "user", cascade = CascadeType.PERSIST, orphanRemoval = false)
    private List<Diary> diaries = new ArrayList<>();

    @Builder
    public User(String email,String passwordHash, String nickname, UserStatus userStatus){
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.userStatus = userStatus;
    }

    // Diary에서 사용할 메서드
    public void addDiary(Diary diary) {
        diaries.add(diary);
    }

    public void updateUser(String email, String nickname, UserStatus userStatus) {
        this.email = email;
        this.nickname = nickname;
        this.userStatus = userStatus;
    }

    public void changeUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }

}
