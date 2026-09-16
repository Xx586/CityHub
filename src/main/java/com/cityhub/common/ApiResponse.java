package com.cityhub.common;

import com.cityhub.web.TraceIdFilter;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final Long total;
    private final String traceId;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "success", data, null, TraceIdFilter.currentTraceId());
    }

    public static <T> ApiResponse<T> page(T data, long total) {
        return new ApiResponse<>(true, "OK", "success", data, total, TraceIdFilter.currentTraceId());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null, null, TraceIdFilter.currentTraceId());
    }
}

