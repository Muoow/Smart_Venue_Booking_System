package com.courtflow.homework.common.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VenueRecommendationVO {

    private Long venueId;

    private String venueName;

    private Long resourceId;

    private String resourceName;

    private Integer estimatedPrice;

    private Integer unitMinutes;

    private Integer capacity;

    private Double score;

    private List<String> reasonList;
}
