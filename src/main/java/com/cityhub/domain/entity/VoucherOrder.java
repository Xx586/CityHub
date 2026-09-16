package com.cityhub.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cityhub.domain.enums.VoucherOrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_voucher_order")
public class VoucherOrder {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long voucherId;
    private Integer payType;
    private VoucherOrderStatus status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime cancelTime;
    private LocalDateTime updateTime;
}

