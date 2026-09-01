package com.campuslink.controller;
import com.campuslink.common.ApiResponse;import com.campuslink.service.StorageService;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;import java.util.Map;
@RestController @RequestMapping("/api/files") @RequiredArgsConstructor public class FileController {private final StorageService service;@PostMapping("/images") public ApiResponse<Map<String,Object>> image(@RequestPart("file") MultipartFile file){return ApiResponse.ok(service.storeImage(file));}}
