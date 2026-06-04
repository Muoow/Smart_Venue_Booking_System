package com.courtflow.homework.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentBizTypeEnum implements IEnum<Integer> {

    PAY(1, "支付"),

    REFUND(2, "退款");

    private final Integer code;
    private final String desc;

    PaymentBizTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public Integer getValue() {
        return code;
    }

    @JsonValue
    public Integer getJsonValue() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
