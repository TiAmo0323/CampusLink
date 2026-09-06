package com.campuslink.controller;

import com.campuslink.common.ApiResponse;
import com.campuslink.config.AuthContext;
import com.campuslink.dto.Requests;
import com.campuslink.service.AccountService;
import com.campuslink.service.SkillService;
import com.campuslink.vo.Views;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SkillController {
    private final SkillService service;
    private final AccountService accounts;

    @GetMapping("/api/public/skills")
    public ApiResponse<Views.PageVO<Object>> skills(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(Views.PageVO.from(service.skills(page,size)));}
    @GetMapping("/api/skills/profile")
    public ApiResponse<Views.SkillProfileVO> profile(@RequestParam(defaultValue="1") int offerPage,@RequestParam(defaultValue="10") int offerSize,@RequestParam(defaultValue="1") int needPage,@RequestParam(defaultValue="10") int needSize){return ApiResponse.ok(new Views.SkillProfileVO(service.profile(AuthContext.userId(),offerPage,offerSize,needPage,needSize)));}
    @PostMapping({"/api/skills/offers","/api/users/skills"})
    public ApiResponse<Views.UserVO> offer(@Valid @RequestBody Requests.SkillOffer dto){Long userId=AuthContext.userId();service.saveOffer(userId,dto);return ApiResponse.ok(new Views.UserVO(accounts.profile(userId)));}
    @DeleteMapping("/api/skills/offers/{id}")
    public ApiResponse<Void> deleteOffer(@PathVariable Long id){service.deleteOffer(AuthContext.userId(),id);return ApiResponse.ok();}
    @PostMapping({"/api/skills/needs","/api/users/skill-needs"})
    public ApiResponse<Views.UserVO> need(@Valid @RequestBody Requests.SkillNeed dto){Long userId=AuthContext.userId();service.saveNeed(userId,dto);return ApiResponse.ok(new Views.UserVO(accounts.profile(userId)));}
    @DeleteMapping("/api/skills/needs/{id}")
    public ApiResponse<Void> deleteNeed(@PathVariable Long id){service.deleteNeed(AuthContext.userId(),id);return ApiResponse.ok();}
    @GetMapping({"/api/skills/matches","/api/skills/match"})
    public ApiResponse<Views.PageVO<Object>> matches(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(Views.PageVO.from(service.matches(AuthContext.userId(),page,size)));}
    @PostMapping("/api/exchanges")
    public ApiResponse<Views.SkillExchangeVO> exchange(@Valid @RequestBody Requests.Exchange dto){return ApiResponse.ok(Views.SkillExchangeVO.from(service.createExchange(AuthContext.userId(),dto)));}
    @GetMapping("/api/exchanges")
    public ApiResponse<Views.PageVO<Object>> exchanges(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(Views.PageVO.from(service.exchanges(AuthContext.userId(),page,size)));}
    @PostMapping("/api/exchanges/{id}/{action}")
    public ApiResponse<Views.SkillExchangeVO> action(@PathVariable Long id,@PathVariable String action){return ApiResponse.ok(Views.SkillExchangeVO.from(service.changeExchange(id,AuthContext.userId(),action)));}
}
