package com.courtflow.homework.common.dto.request;

import lombok.Data;

import java.util.Date;

@Data
public class ReservationApplyRequest {

    private Long userId;
    private Long venueId;
    private Long resourceId;
    private Date slotDate;
    private Integer startUnit;
    private Integer endUnit;
    private Integer size;
}
