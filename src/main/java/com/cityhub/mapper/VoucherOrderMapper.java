package com.cityhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cityhub.domain.entity.VoucherOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
    @Update("""
            UPDATE tb_voucher_order SET status = 'PAID', pay_time = NOW()
            WHERE id = #{orderId} AND user_id = #{userId} AND status = 'UNPAID'
            """)
    int markPaid(@Param("orderId") Long orderId, @Param("userId") Long userId);

    @Update("""
            UPDATE tb_voucher_order SET status = 'CANCELED', cancel_time = NOW()
            WHERE id = #{orderId} AND status = 'UNPAID'
            """)
    int markCanceled(@Param("orderId") Long orderId);

    @Select("""
            SELECT id FROM tb_voucher_order
            WHERE status = 'UNPAID' AND create_time < #{deadline}
            ORDER BY create_time LIMIT #{batchSize}
            """)
    List<Long> selectExpiredIds(@Param("deadline") LocalDateTime deadline, @Param("batchSize") int batchSize);
}

