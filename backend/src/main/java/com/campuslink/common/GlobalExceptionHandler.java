package com.campuslink.common;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> business(BusinessException e) { return ApiResponse.error(e.getCode(), e.getMessage()); }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ApiResponse<Void> validation(Exception e) {
        String message = e instanceof MethodArgumentNotValidException m && m.getBindingResult().getFieldError() != null
                ? m.getBindingResult().getFieldError().getDefaultMessage() : "请求参数格式错误";
        return ApiResponse.error(400, message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> unexpected(Exception e) {
        e.printStackTrace();
        return ApiResponse.error(500, "服务器内部错误");
    }
}
