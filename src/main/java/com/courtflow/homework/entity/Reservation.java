package com.courtflow.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("reservation")
public class Reservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long venueId;
    private Long resourceId;
    private Long orderId;
    private Date slotDate;
    private Integer size;
    private Integer startUnit;
    private Integer endUnit;
    private ReservationStatusEnum status;
    private Date createdAt;
    private Date updatedAt;
}
