package com.courtflow.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@TableName("venue_admin")
public class VenueAdmin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long venueId;
}
