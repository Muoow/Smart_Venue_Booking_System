package com.courtflow.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.courtflow.homework.common.enums.TimeSlotStatusEnum;
import lombok.Builder;
import lombok.Data;

import java.sql.Time;
import java.util.Date;

@Builder
@Data
@TableName("time_slot")
public class TimeSlot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resourceId;
    private Date slotDate;
    private Integer slotUnit;
    private TimeSlotStatusEnum status;
    private Integer bookedCount;
    private Date createdAt;
    private Date updatedAt;
}
