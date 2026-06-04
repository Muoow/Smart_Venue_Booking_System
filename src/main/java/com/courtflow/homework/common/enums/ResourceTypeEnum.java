package com.courtflow.homework.common.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourceTypeEnum implements IEnum<Integer> {

    BADMINTON(1, "羽毛球"),

    BASKETBALL(2, "篮球"),

    TENNIS(3, "网球"),

    FOOTBALL(4, "足球"),

    TABLE_TENNIS(5, "乒乓球"),

    SWIMMING(6, "游泳"),

    MULTI_PURPOSE(7, "综合场地");

    private final Integer code;

    private final String desc;

    ResourceTypeEnum(Integer code, String desc) {
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
