package com.courtflow.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.courtflow.homework.common.enums.ResourceStatusEnum;
import com.courtflow.homework.common.enums.ResourceTypeEnum;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@TableName("venue_resource")
public class VenueResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long venueId;
    private String name;
    private ResourceTypeEnum resourceType;
    private Integer capacity;
    private Integer price;
    private Integer unitMinutes;
    private ResourceStatusEnum status;
}