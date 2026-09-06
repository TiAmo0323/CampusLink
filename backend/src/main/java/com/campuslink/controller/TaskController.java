package com.campuslink.controller;

import com.campuslink.common.ApiResponse;
import com.campuslink.config.AuthContext;
import com.campuslink.dto.Requests;
import com.campuslink.service.AbnormalTaskService;
import com.campuslink.service.TaskService;
import com.campuslink.vo.Views;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class TaskController {
    private final TaskService service;
    private final AbnormalTaskService abnormalTaskService;

    @GetMapping("/api/public/tasks")
    public ApiResponse<Views.PageVO<Object>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String keyword,@RequestParam(required=false) String category,@RequestParam(required=false) String status,@RequestParam(required=false) String location,@RequestParam(required=false) Integer minReward,@RequestParam(required=false) Integer maxReward,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,@RequestParam(defaultValue="deadline") String sort){
        return ApiResponse.ok(Views.PageVO.from(service.list(page,size,keyword,category,status,location,minReward,maxReward,startTime,endTime,sort)));
    }
    @GetMapping("/api/public/tasks/{id}")
    public ApiResponse<Views.TaskDetailVO> detail(@PathVariable Long id){return ApiResponse.ok(new Views.TaskDetailVO(service.detail(id)));}
    @PostMapping("/api/tasks")
    public ApiResponse<Views.TaskDetailVO> publish(@Valid @RequestBody Requests.TaskCreate dto){return ApiResponse.ok(new Views.TaskDetailVO(service.publish(AuthContext.userId(),dto)));}
    @GetMapping("/api/tasks/mine")
    public ApiResponse<Views.PageVO<Object>> mine(@RequestParam(defaultValue="published") String kind,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(Views.PageVO.from(service.mine(AuthContext.userId(),kind,page,size)));}
    @PostMapping("/api/tasks/{id}/applications")
    public ApiResponse<Views.TaskApplicationVO> apply(@PathVariable Long id,@Valid @RequestBody Requests.TaskApply dto){return ApiResponse.ok(Views.TaskApplicationVO.from(service.apply(id,AuthContext.userId(),dto)));}
    @GetMapping("/api/tasks/{id}/applications")
    public ApiResponse<Views.PageVO<Object>> applications(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(Views.PageVO.from(service.applications(id,AuthContext.userId(),page,size)));}
    @GetMapping("/api/tasks/{id}/applicants/{applicantId}")
    public ApiResponse<Views.ApplicantProfileVO> applicant(@PathVariable Long id,@PathVariable Long applicantId,@RequestParam(defaultValue="1") int skillPage,@RequestParam(defaultValue="10") int skillSize,@RequestParam(defaultValue="1") int reviewPage,@RequestParam(defaultValue="10") int reviewSize){return ApiResponse.ok(new Views.ApplicantProfileVO(service.applicantProfile(id,applicantId,AuthContext.userId(),skillPage,skillSize,reviewPage,reviewSize)));}
    @PostMapping("/api/applications/{id}/accept")
    public ApiResponse<Views.TaskOrderVO> accept(@PathVariable Long id){return ApiResponse.ok(new Views.TaskOrderVO(service.acceptApplication(id,AuthContext.userId())));}
    @PostMapping("/api/tasks/{taskId}/applications/{applicationId}/accept")
    public ApiResponse<Views.TaskOrderVO> accept(@PathVariable Long taskId,@PathVariable Long applicationId){return ApiResponse.ok(new Views.TaskOrderVO(service.acceptApplication(taskId,applicationId,AuthContext.userId())));}
    @PostMapping("/api/tasks/{id}/cancel")
    public ApiResponse<Views.TaskDetailVO> cancel(@PathVariable Long id,@RequestBody(required=false) Requests.Cancel dto){return ApiResponse.ok(new Views.TaskDetailVO(service.cancel(id,AuthContext.userId(),dto==null?null:dto.reason())));}
    @PostMapping("/api/applications/{id}/withdraw")
    public ApiResponse<Void> withdraw(@PathVariable Long id){service.withdraw(id,AuthContext.userId());return ApiResponse.ok();}
    @PostMapping("/api/tasks/{id}/reassign")
    public ApiResponse<Views.TaskOrderVO> reassign(@PathVariable Long id,@Valid @RequestBody Requests.Reassign dto){return ApiResponse.ok(new Views.TaskOrderVO(service.reassign(id,AuthContext.userId(),dto)));}
    @PostMapping("/api/tasks/{id}/abnormal")
    public ApiResponse<Views.ReportVO> abnormal(@PathVariable Long id,@Valid @RequestBody Requests.AbnormalCreate dto){return ApiResponse.ok(Views.ReportVO.from(abnormalTaskService.open(id,AuthContext.userId(),dto)));}
    @GetMapping("/api/orders/{id}")
    public ApiResponse<Views.TaskOrderVO> order(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size){return ApiResponse.ok(new Views.TaskOrderVO(service.order(id,AuthContext.userId(),page,size)));}
    @PostMapping("/api/orders/{id}/start")
    public ApiResponse<Views.TaskOrderVO> start(@PathVariable Long id){return ApiResponse.ok(new Views.TaskOrderVO(service.startOrder(id,AuthContext.userId())));}
    @PostMapping("/api/orders/{id}/completions")
    public ApiResponse<Views.TaskCompletionVO> complete(@PathVariable Long id,@Valid @RequestBody Requests.Completion dto){return ApiResponse.ok(Views.TaskCompletionVO.from(service.submitCompletion(id,AuthContext.userId(),dto)));}
    @PostMapping({"/api/orders/{id}/approve","/api/orders/{id}/accept"})
    public ApiResponse<Views.TaskOrderVO> approve(@PathVariable Long id){return ApiResponse.ok(new Views.TaskOrderVO(service.approve(id,AuthContext.userId())));}
    @PostMapping("/api/orders/{id}/reject")
    public ApiResponse<Views.TaskOrderVO> reject(@PathVariable Long id,@Valid @RequestBody Requests.Reject dto){return ApiResponse.ok(new Views.TaskOrderVO(service.reject(id,AuthContext.userId(),dto)));}
}
