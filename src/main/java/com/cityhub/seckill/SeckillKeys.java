package com.cityhub.seckill;

public final class SeckillKeys {
    public static final String OUTBOX_STREAM = "stream:outbox:voucher-orders";
    public static final String OUTBOX_GROUP = "voucher-order-kafka-relay";

    private SeckillKeys() {
    }

    public static String stock(Long voucherId) {
        return "seckill:stock:" + voucherId;
    }

    public static String orders(Long voucherId) {
        return "seckill:orders:" + voucherId;
    }

    public static String begin(Long voucherId) {
        return "seckill:begin:" + voucherId;
    }

    public static String end(Long voucherId) {
        return "seckill:end:" + voucherId;
    }

    public static String orderStatus(Long orderId) {
        return "seckill:order:status:" + orderId;
    }
}

