package com.courtflow.homework.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimeSlotStatusEnum implements IEnum<Integer> {

    FREE(0, "空闲"),

    FULL(1, "已满"),

    BLOCKED(2, "禁用");

    private final Integer code;
    private final String  desc;

    TimeSlotStatusEnum(Integer code, String desc) {
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
