package com.campuslink.controller;

import com.campuslink.common.ApiResponse;
import com.campuslink.service.StorageService;
import com.campuslink.vo.Views;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final StorageService service;

    @PostMapping("/proofs")
    public ApiResponse<Views.UploadVO> proof(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(Views.UploadVO.from(service.storeProof(file)));
    }

    @PostMapping("/images")
    public ApiResponse<Views.UploadVO> image(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(Views.UploadVO.from(service.storeImage(file)));
    }
}
