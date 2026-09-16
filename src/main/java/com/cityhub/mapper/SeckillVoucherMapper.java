package com.cityhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cityhub.domain.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {
    @Update("UPDATE tb_seckill_voucher SET stock = stock - 1 WHERE voucher_id = #{voucherId} AND stock > 0")
    int decrementStock(@Param("voucherId") Long voucherId);

    @Update("UPDATE tb_seckill_voucher SET stock = stock + 1 WHERE voucher_id = #{voucherId}")
    int incrementStock(@Param("voucherId") Long voucherId);
}

