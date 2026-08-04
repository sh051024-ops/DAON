package com.example.demo.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
@Getter @Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 255)
    private String pw;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(length = 255)
    private String photo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AppUser(String id, String pw, String name, String role, String photo) {
        this.id = id;
        this.pw = pw;
        this.name = name;
        this.role = role;
        this.photo = photo;
    }
}
