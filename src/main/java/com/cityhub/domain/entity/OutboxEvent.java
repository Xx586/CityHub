package com.cityhub.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cityhub.domain.enums.OutboxStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_outbox_event")
public class OutboxEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private OutboxStatus status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

