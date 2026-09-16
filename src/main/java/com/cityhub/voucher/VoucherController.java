package com.cityhub.voucher;

import com.cityhub.common.ApiResponse;
import com.cityhub.domain.entity.Voucher;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/voucher")
@RequiredArgsConstructor
public class VoucherController {
    private final VoucherService voucherService;

    @GetMapping("/list/{shopId}")
    public ApiResponse<List<Voucher>> findByShop(@PathVariable Long shopId) {
        return ApiResponse.ok(voucherService.findByShopId(shopId));
    }

    @PostMapping("/seckill")
    public ApiResponse<Long> createSeckillVoucher(@Valid @RequestBody CreateSeckillVoucherRequest request) {
        return ApiResponse.ok(voucherService.createSeckillVoucher(request));
    }
}
