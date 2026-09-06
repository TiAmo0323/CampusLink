package com.campuslink.controller;

import com.campuslink.common.ApiResponse;
import com.campuslink.config.AuthContext;
import com.campuslink.dto.Requests;
import com.campuslink.service.AbnormalTaskService;
import com.campuslink.service.GovernanceService;
import com.campuslink.vo.Views;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GovernanceController {
    private final GovernanceService service;
    private final AbnormalTaskService abnormalTaskService;

    @PostMapping("/reviews")
    public ApiResponse<Views.ReviewVO> review(@Valid @RequestBody Requests.ReviewCreate dto){return ApiResponse.ok(Views.ReviewVO.from(service.review(AuthContext.userId(),dto)));}
    @GetMapping("/reviews/users/{id}")
    public ApiResponse<Views.PageVO<Object>> reviews(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(Views.PageVO.from(service.reviews(id,page,size)));}
    @PostMapping("/reports")
    public ApiResponse<Views.ReportVO> report(@Valid @RequestBody Requests.ReportCreate dto){return ApiResponse.ok(Views.ReportVO.from("ORDER".equals(dto.targetType())?abnormalTaskService.openOrder(dto.targetId(),AuthContext.userId(),new Requests.AbnormalCreate(dto.reasonType(),dto.description())):service.report(AuthContext.userId(),dto)));}
    @GetMapping("/reports/mine")
    public ApiResponse<Views.PageVO<Object>> mine(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(Views.PageVO.from(service.myReports(AuthContext.userId(),page,size)));}
    @GetMapping("/admin/dashboard")
    public ApiResponse<Views.DashboardVO> dashboard(){AuthContext.requireAdmin();return ApiResponse.ok(new Views.DashboardVO(service.dashboard()));}
    @GetMapping("/admin/users")
    public ApiResponse<Views.PageVO<Object>> users(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String status){AuthContext.requireAdmin();return ApiResponse.ok(Views.PageVO.from(service.users(page,size,status)));}
    @PutMapping("/admin/users/{id}/status/{status}")
    public ApiResponse<Views.UserVO> userStatus(@PathVariable Long id,@PathVariable String status,@Valid @RequestBody(required=false) Requests.AdminUserStatus dto){AuthContext.requireAdmin();return ApiResponse.ok(new Views.UserVO(service.setUserStatus(AuthContext.userId(),id,status,dto==null?"管理员直接更新账户状态":dto.reason())));}
    @GetMapping("/admin/tasks")
    public ApiResponse<Views.PageVO<Object>> tasks(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String status){AuthContext.requireAdmin();return ApiResponse.ok(Views.PageVO.from(service.tasks(page,size,status)));}
    @GetMapping("/admin/reports")
    public ApiResponse<Views.PageVO<Object>> reports(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String status){AuthContext.requireAdmin();return ApiResponse.ok(Views.PageVO.from(service.reports(page,size,status)));}
    @GetMapping("/admin/actions")
    public ApiResponse<Views.PageVO<Object>> actions(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){AuthContext.requireAdmin();return ApiResponse.ok(Views.PageVO.from(service.adminActions(page,size)));}
    @PostMapping("/admin/reports/{id}/handle")
    public ApiResponse<Views.ReportVO> handle(@PathVariable Long id,@Valid @RequestBody Requests.ReportHandle dto){AuthContext.requireAdmin();return ApiResponse.ok(Views.ReportVO.from(service.handleReport(AuthContext.userId(),id,dto)));}
    @GetMapping("/admin/reports/{id}/evidence")
    public ApiResponse<Views.ReportEvidenceVO> evidence(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){AuthContext.requireAdmin();return ApiResponse.ok(new Views.ReportEvidenceVO(service.evidence(id,page,size)));}
    @PostMapping("/admin/abnormal-tasks/{reportId}/resolve")
    public ApiResponse<Views.ReportVO> resolveAbnormal(@PathVariable Long reportId,@Valid @RequestBody Requests.AbnormalResolve dto){AuthContext.requireAdmin();return ApiResponse.ok(Views.ReportVO.from(abnormalTaskService.resolve(reportId,AuthContext.userId(),dto)));}
    @PostMapping("/admin/tasks/{id}/delist")
    public ApiResponse<Void> delist(@PathVariable Long id,@Valid @RequestBody Requests.Cancel dto){AuthContext.requireAdmin();service.delist(AuthContext.userId(),id,dto.reason());return ApiResponse.ok();}
    @PostMapping("/admin/reports/{id}/claim")
    public ApiResponse<Views.ReportVO> claim(@PathVariable Long id){AuthContext.requireAdmin();return ApiResponse.ok(Views.ReportVO.from(service.claimReport(id,AuthContext.userId())));}
    @PostMapping("/admin/skills")
    public ApiResponse<Views.SkillVO> addSkill(@Valid @RequestBody Requests.AdminSkillCreate body){AuthContext.requireAdmin();return ApiResponse.ok(Views.SkillVO.from(service.addSkill(body.name(),body.category())));}
    @GetMapping("/admin/skills")
    public ApiResponse<Views.PageVO<Object>> allSkills(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){AuthContext.requireAdmin();return ApiResponse.ok(Views.PageVO.from(service.allSkills(page,size)));}
    @PutMapping("/admin/skills/{id}/status/{status}")
    public ApiResponse<Views.SkillVO> skillStatus(@PathVariable Long id,@PathVariable String status,@Valid @RequestBody(required=false) Requests.AdminUserStatus dto){AuthContext.requireAdmin();return ApiResponse.ok(Views.SkillVO.from(service.setSkillStatus(AuthContext.userId(),id,status,dto==null?"管理员更新技能状态":dto.reason())));}
}
