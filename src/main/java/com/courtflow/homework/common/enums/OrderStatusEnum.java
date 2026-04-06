package com.courtflow.homework.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatusEnum implements IEnum<Integer> {

    UNPAID(0, "未支付"),

    PAID(1, "已支付"),

    CLOSED(2, "已取消"),

    REFUNDED(3, "已退款");

    private final Integer code;
    private final String  desc;

    OrderStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public Integer getValue() {
        return this.code;
    }

    @JsonValue
    public Integer getJsonValue() {
        return this.code;
    }

}
