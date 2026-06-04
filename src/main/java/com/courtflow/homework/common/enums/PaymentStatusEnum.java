package com.courtflow.homework.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatusEnum implements IEnum<Integer> {

    PROCESSING(0, "处理中"),

    SUCCESS(1, "成功"),

    FAILED(2, "失败"),

    CLOSED(3, "已关闭");

    private final Integer code;
    private final String desc;

    PaymentStatusEnum(Integer code, String desc) {
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
