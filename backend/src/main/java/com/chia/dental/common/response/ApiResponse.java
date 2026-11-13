package com.chia.dental.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;
    private OffsetDateTime timestamp;
    private List<FieldErrorDetail> fieldErrors;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("OK")
                .message("Thành công")
                .data(data)
                .timestamp(OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("OK")
                .message(message)
                .data(data)
                .timestamp(OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, List<FieldErrorDetail> fieldErrors) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .fieldErrors(fieldErrors)
                .timestamp(OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
    }
}

