package com.campuslink.controller;

import com.campuslink.common.ApiResponse;
import com.campuslink.dto.Requests;
import com.campuslink.service.AuthService;
import com.campuslink.service.SessionService;
import com.campuslink.vo.Views;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService service;
    private final SessionService sessions;

    @PostMapping("/refresh")
    public ApiResponse<Views.LoginVO> refresh(@Valid @RequestBody Requests.Refresh dto) {
        return ApiResponse.ok(new Views.LoginVO(sessions.refresh(dto.refreshToken())));
    }
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody Requests.Refresh dto) {
        sessions.logout(dto.refreshToken());
        return ApiResponse.ok();
    }
    @PostMapping("/register")
    public ApiResponse<Views.LoginVO> register(@Valid @RequestBody Requests.Register dto) {
        return ApiResponse.ok(new Views.LoginVO(service.register(dto)));
    }
    @PostMapping("/login")
    public ApiResponse<Views.LoginVO> login(@Valid @RequestBody Requests.Login dto) {
        return ApiResponse.ok(new Views.LoginVO(service.login(dto)));
    }
}
