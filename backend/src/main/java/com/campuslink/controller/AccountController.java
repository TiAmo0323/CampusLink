package com.campuslink.controller;

import com.campuslink.common.ApiResponse;
import com.campuslink.config.AuthContext;
import com.campuslink.dto.Requests;
import com.campuslink.service.AccountService;
import com.campuslink.vo.Views;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService service;

    @GetMapping("/users/me")
    public ApiResponse<Views.UserVO> me(){return ApiResponse.ok(new Views.UserVO(service.profile(AuthContext.userId())));}
    @PutMapping("/users/me")
    public ApiResponse<Views.UserVO> update(@Valid @RequestBody Requests.Profile dto){return ApiResponse.ok(new Views.UserVO(service.update(AuthContext.userId(),dto)));}
    @GetMapping("/wallet")
    public ApiResponse<Views.WalletVO> wallet(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(new Views.WalletVO(service.wallet(AuthContext.userId(),page,size)));}
    @GetMapping("/points/transactions")
    public ApiResponse<Views.PageVO<Object>> pointTransactions(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(Views.PageVO.from(service.pointTransactions(AuthContext.userId(),page,size)));}
    @GetMapping("/notifications")
    public ApiResponse<Views.PageVO<Object>> notifications(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(Views.PageVO.from(service.notifications(AuthContext.userId(),page,size)));}
    @PostMapping("/notifications/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id){service.readNotification(AuthContext.userId(),id);return ApiResponse.ok();}
}
