package com.courtflow.homework.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourceStatusEnum implements IEnum<Integer> {

    DISABLED(0, "禁用"),

    ENABLED(1, "启用");

    private final Integer code;
    private final String  desc;

    ResourceStatusEnum(Integer code, String desc) {
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
