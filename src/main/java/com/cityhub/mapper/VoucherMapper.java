package com.cityhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cityhub.domain.entity.Voucher;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VoucherMapper extends BaseMapper<Voucher> {
    @Select("""
            SELECT v.*, sv.stock, sv.begin_time, sv.end_time
            FROM tb_voucher v
            LEFT JOIN tb_seckill_voucher sv ON v.id = sv.voucher_id
            WHERE v.shop_id = #{shopId} AND v.status = 1
            ORDER BY v.id
            """)
    List<Voucher> selectByShopId(@Param("shopId") Long shopId);
}

