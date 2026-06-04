package com.courtflow.homework.common.dto.request;

import lombok.Data;

@Data
public class AiVenueRecommendRequest {

    private String sportKeyword;

    private Integer expectedStartUnit;

    private Integer expectedEndUnit;

    private Integer preferredUnitMinutes;

    private Integer expectedPeopleCount;

    private Integer maxBudget;

    private Boolean preferLowPrice;

    private Boolean preferLargeCapacity;

    private Integer topN = 3;
}
