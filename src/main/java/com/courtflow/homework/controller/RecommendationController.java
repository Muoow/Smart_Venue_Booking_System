package com.courtflow.homework.controller;

import com.courtflow.homework.common.annonation.CheckToken;
import com.courtflow.homework.common.dto.request.AiVenueRecommendRequest;
import com.courtflow.homework.common.dto.response.ApiResponse;
import com.courtflow.homework.common.vo.VenueRecommendationVO;
import com.courtflow.homework.service.VenueRecommendationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CheckToken(required = false)
@RequestMapping("/recommendation")
public class RecommendationController {

    private final VenueRecommendationService venueRecommendationService;

    public RecommendationController(VenueRecommendationService venueRecommendationService) {
        this.venueRecommendationService = venueRecommendationService;
    }

    @PostMapping("/venues")
    public ApiResponse<List<VenueRecommendationVO>> recommend(@RequestBody AiVenueRecommendRequest request) {
        return ApiResponse.success(venueRecommendationService.recommend(request));
    }
}
