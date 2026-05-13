package com.localrag.auth.controller;

import com.localrag.auth.config.JwtProperties;
import com.localrag.auth.dto.LoginRequest;
import com.localrag.auth.dto.RegisterRequest;
import com.localrag.auth.dto.TokenResponse;
import com.localrag.auth.model.User;
import com.localrag.auth.repository.UserRepository;
import com.localrag.auth.security.JwtUtils;
import com.localrag.common.Result;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    public Result<TokenResponse> register(@RequestBody RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码长度至少8位");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已被占用");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .build();
        userRepository.save(user);

        String token = jwtUtils.generate(user.getId(), user.getUsername(), user.getRole());
        log.info("user registered: username={}, userId={}", user.getUsername(), user.getId());

        return Result.ok(TokenResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build());
    }

    @PostMapping("/login")
    public Result<TokenResponse> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        String token = jwtUtils.generate(user.getId(), user.getUsername(), user.getRole());
        log.info("user logged in: username={}", user.getUsername());

        return Result.ok(TokenResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build());
    }

    @PostConstruct
    void bootstrapAdmin() {
        JwtProperties.AdminBootstrap admin = jwtProperties.getAdminBootstrap();
        if (!admin.isEnabled()) {
            return;
        }
        if (userRepository.existsByUsername(admin.getUsername())) {
            log.info("admin user already exists, skip bootstrap");
            return;
        }
        if (admin.getPassword() == null || admin.getPassword().isBlank()) {
            log.warn("admin bootstrap enabled but no password configured");
            return;
        }
        User adminUser = User.builder()
                .username(admin.getUsername())
                .password(passwordEncoder.encode(admin.getPassword()))
                .role("ADMIN")
                .build();
        userRepository.save(adminUser);
        log.info("admin user bootstrapped: username={}", adminUser.getUsername());
    }
}
