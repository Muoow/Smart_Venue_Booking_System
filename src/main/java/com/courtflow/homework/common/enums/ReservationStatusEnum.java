package com.courtflow.homework.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReservationStatusEnum implements IEnum<Integer> {

    QUEUING(0, "排队中"),

    RESERVED(1, "已占位"),

    CANCELLED(2, "已取消"),

    EXPIRED(3, "已释放"),

    FINISHED(4, "已完成");

    private final Integer code;
    private final String  desc;

    ReservationStatusEnum(Integer code, String desc) {
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
