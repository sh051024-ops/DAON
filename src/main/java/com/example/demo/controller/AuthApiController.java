package com.example.demo.controller;

import com.example.demo.domain.auth.AppUser;
import com.example.demo.domain.auth.AppUserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

/**
 * 사용자 계정 인증 및 관리 컨트롤러. DB(H2/PostgreSQL)를 기반으로 사용자를 인증하고 패스워드를 변경합니다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AppUserRepository appUserRepository;

    public AuthApiController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public record LoginRequest(String id, String pw) {}
    public record LoginResult(boolean success, String message, UserDto user) {}
    public record UserDto(String id, String name, String role, String photo) {}

    public record ChangePasswordRequest(String id, String currentPassword, String newPassword) {}
    public record ChangePasswordResult(boolean success, String message) {}

    @PostMapping("/login")
    public LoginResult login(@RequestBody LoginRequest req) {
        if (req == null || req.id() == null || req.pw() == null) {
            return new LoginResult(false, "아이디와 비밀번호를 입력해주세요.", null);
        }
        Optional<AppUser> opt = appUserRepository.findById(req.id().trim());
        if (opt.isEmpty() || !opt.get().getPw().equals(req.pw())) {
            return new LoginResult(false, "아이디 또는 비밀번호가 올바르지 않습니다.", null);
        }
        AppUser u = opt.get();
        return new LoginResult(true, "로그인 성공", new UserDto(u.getId(), u.getName(), u.getRole(), u.getPhoto()));
    }

    @PostMapping("/change-password")
    @Transactional
    public ChangePasswordResult changePassword(@RequestBody ChangePasswordRequest req) {
        if (req == null || req.newPassword() == null || req.newPassword().trim().length() < 4) {
            return new ChangePasswordResult(false, "새 비밀번호는 4자 이상이어야 합니다.");
        }
        Optional<AppUser> opt = appUserRepository.findById(req.id());
        if (opt.isEmpty()) {
            return new ChangePasswordResult(false, "계정을 찾을 수 없습니다.");
        }
        AppUser u = opt.get();
        if (!u.getPw().equals(req.currentPassword())) {
            return new ChangePasswordResult(false, "현재 비밀번호가 올바르지 않습니다.");
        }
        u.setPw(req.newPassword());
        appUserRepository.save(u);
        return new ChangePasswordResult(true, "비밀번호가 변경되었습니다.");
    }
}
