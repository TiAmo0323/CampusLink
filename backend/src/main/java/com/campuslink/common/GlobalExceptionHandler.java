package com.campuslink.common;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        int status=e.getCode()>=400&&e.getCode()<=599?e.getCode():400;
        return org.springframework.http.ResponseEntity.status(status).body(ApiResponse.error(e.getCode(),e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> validation(Exception e) {
        String message = e instanceof MethodArgumentNotValidException m && m.getBindingResult().getFieldError() != null
                ? m.getBindingResult().getFieldError().getDefaultMessage() : "请求参数格式错误";
        return org.springframework.http.ResponseEntity.badRequest().body(ApiResponse.error(400,message));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> requestParameter(Exception e) {
        return org.springframework.http.ResponseEntity.badRequest().body(ApiResponse.error(400,"请求参数格式错误"));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> notFound(NoResourceFoundException e) {
        return org.springframework.http.ResponseEntity.status(404).body(ApiResponse.error(404,"接口或资源不存在"));
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return org.springframework.http.ResponseEntity.status(405).body(ApiResponse.error(405,"请求方法不支持"));
    }

    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        log.error("Unhandled request exception",e);
        return org.springframework.http.ResponseEntity.internalServerError().body(ApiResponse.error(500,"服务器内部错误"));
    }
}
