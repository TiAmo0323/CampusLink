package com.campuslink.controller;
import com.campuslink.common.ApiResponse;import com.campuslink.config.AuthContext;import com.campuslink.domain.*;import com.campuslink.dto.Requests;import com.campuslink.service.SkillService;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequiredArgsConstructor public class SkillController {private final SkillService service;
 @GetMapping("/api/public/skills") public ApiResponse<List<Skill>> skills(){return ApiResponse.ok(service.skills());}
 @GetMapping("/api/skills/profile") public ApiResponse<Map<String,Object>> profile(){return ApiResponse.ok(service.profile(AuthContext.userId()));}
 @PostMapping("/api/skills/offers") public ApiResponse<Void> offer(@Valid @RequestBody Requests.SkillOffer dto){service.saveOffer(AuthContext.userId(),dto);return ApiResponse.ok();}
 @DeleteMapping("/api/skills/offers/{id}") public ApiResponse<Void> deleteOffer(@PathVariable Long id){service.deleteOffer(AuthContext.userId(),id);return ApiResponse.ok();}
 @PostMapping("/api/skills/needs") public ApiResponse<Void> need(@Valid @RequestBody Requests.SkillNeed dto){service.saveNeed(AuthContext.userId(),dto);return ApiResponse.ok();}
 @DeleteMapping("/api/skills/needs/{id}") public ApiResponse<Void> deleteNeed(@PathVariable Long id){service.deleteNeed(AuthContext.userId(),id);return ApiResponse.ok();}
 @GetMapping("/api/skills/matches") public ApiResponse<List<Map<String,Object>>> matches(){return ApiResponse.ok(service.matches(AuthContext.userId()));}
 @PostMapping("/api/exchanges") public ApiResponse<SkillExchange> exchange(@Valid @RequestBody Requests.Exchange dto){return ApiResponse.ok(service.createExchange(AuthContext.userId(),dto));}
 @GetMapping("/api/exchanges") public ApiResponse<List<Map<String,Object>>> exchanges(){return ApiResponse.ok(service.exchanges(AuthContext.userId()));}
 @PostMapping("/api/exchanges/{id}/{action}") public ApiResponse<SkillExchange> action(@PathVariable Long id,@PathVariable String action){return ApiResponse.ok(service.changeExchange(id,AuthContext.userId(),action));}
}
