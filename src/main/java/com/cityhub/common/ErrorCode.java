package com.cityhub.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "请求参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "请先登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "无权执行此操作", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "数据不存在", HttpStatus.NOT_FOUND),
    CONFLICT("CONFLICT", "当前状态不允许此操作", HttpStatus.CONFLICT),
    RATE_LIMITED("RATE_LIMITED", "请求过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    SOLD_OUT("SOLD_OUT", "库存不足", HttpStatus.CONFLICT),
    DUPLICATE_ORDER("DUPLICATE_ORDER", "不能重复下单", HttpStatus.CONFLICT),
    SECKILL_NOT_STARTED("SECKILL_NOT_STARTED", "秒杀尚未开始", HttpStatus.CONFLICT),
    SECKILL_ENDED("SECKILL_ENDED", "秒杀已经结束", HttpStatus.CONFLICT),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}

