package com.cityhub.order;

import com.cityhub.common.ApiResponse;
import com.cityhub.seckill.SeckillResponse;
import com.cityhub.seckill.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher-order")
@RequiredArgsConstructor
public class VoucherOrderController {
    private final SeckillService seckillService;
    private final OrderService orderService;

    @PostMapping("/seckill/{id}")
    public ApiResponse<SeckillResponse> seckill(@PathVariable("id") Long voucherId) {
        return ApiResponse.ok(seckillService.seckill(voucherId));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderView> findById(@PathVariable Long id) {
        return ApiResponse.ok(orderService.findMine(id));
    }

    @GetMapping("/of/me")
    public ApiResponse<PageResult<OrderView>> findMine(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(orderService.findMine(current, size));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<Void> simulatePayment(@PathVariable Long id) {
        orderService.simulatePayment(id);
        return ApiResponse.ok();
    }
}
