package com.courtflow.homework.common.vo;

import com.courtflow.homework.common.enums.ReservationStatusEnum;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class ReservationVO {

    private Long id;
    private Long venueId;
    private Long resourceId;
    private Date slotDate;
    private Integer size;
    private Integer startUnit;
    private Integer endUnit;
    private ReservationStatusEnum status;
}
