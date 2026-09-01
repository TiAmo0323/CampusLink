package com.campuslink.controller;
import com.campuslink.common.ApiResponse; import com.campuslink.config.AuthContext; import com.campuslink.dto.Requests; import com.campuslink.service.TaskService; import com.campuslink.service.AbnormalTaskService; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequiredArgsConstructor public class TaskController { private final TaskService service; private final AbnormalTaskService abnormalTaskService;
 @GetMapping("/api/public/tasks") public ApiResponse<Map<String,Object>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size,@RequestParam(required=false) String keyword,@RequestParam(required=false) String category,@RequestParam(required=false) String status){return ApiResponse.ok(service.list(page,size,keyword,category,status));}
 @GetMapping("/api/public/tasks/{id}") public ApiResponse<Map<String,Object>> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
 @PostMapping("/api/tasks") public ApiResponse<Map<String,Object>> publish(@Valid @RequestBody Requests.TaskCreate dto){return ApiResponse.ok(service.publish(AuthContext.userId(),dto));}
 @GetMapping("/api/tasks/mine") public ApiResponse<Map<String,Object>> mine(){return ApiResponse.ok(service.mine(AuthContext.userId()));}
 @PostMapping("/api/tasks/{id}/applications") public ApiResponse<Void> apply(@PathVariable Long id,@Valid @RequestBody Requests.TaskApply dto){service.apply(id,AuthContext.userId(),dto);return ApiResponse.ok();}
 @GetMapping("/api/tasks/{id}/applications") public ApiResponse<Map<String,Object>> applications(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(service.applications(id,AuthContext.userId(),page,size));}
 @PostMapping("/api/applications/{id}/accept") public ApiResponse<Map<String,Object>> accept(@PathVariable Long id){return ApiResponse.ok(service.acceptApplication(id,AuthContext.userId()));}
 @PostMapping("/api/tasks/{id}/cancel") public ApiResponse<Void> cancel(@PathVariable Long id){service.cancel(id,AuthContext.userId());return ApiResponse.ok();}
 @PostMapping("/api/tasks/{id}/abnormal") public ApiResponse<com.campuslink.domain.Report> abnormal(@PathVariable Long id,@Valid @RequestBody Requests.AbnormalCreate dto){return ApiResponse.ok(abnormalTaskService.open(id,AuthContext.userId(),dto));}
 @GetMapping("/api/orders/{id}") public ApiResponse<Map<String,Object>> order(@PathVariable Long id){return ApiResponse.ok(service.order(id,AuthContext.userId()));}
 @PostMapping("/api/orders/{id}/start") public ApiResponse<Map<String,Object>> start(@PathVariable Long id){return ApiResponse.ok(service.startOrder(id,AuthContext.userId()));}
 @PostMapping("/api/orders/{id}/completions") public ApiResponse<Map<String,Object>> complete(@PathVariable Long id,@Valid @RequestBody Requests.Completion dto){return ApiResponse.ok(service.submitCompletion(id,AuthContext.userId(),dto));}
 @PostMapping("/api/orders/{id}/approve") public ApiResponse<Map<String,Object>> approve(@PathVariable Long id){return ApiResponse.ok(service.approve(id,AuthContext.userId()));}
 @PostMapping("/api/orders/{id}/reject") public ApiResponse<Map<String,Object>> reject(@PathVariable Long id,@Valid @RequestBody Requests.Reject dto){return ApiResponse.ok(service.reject(id,AuthContext.userId(),dto));}
}
