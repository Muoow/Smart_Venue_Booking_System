package com.courtflow.homework.service;

import com.courtflow.homework.common.dto.request.AiVenueRecommendRequest;
import com.courtflow.homework.common.vo.VenueRecommendationVO;

import java.util.List;

public interface VenueRecommendationService {

    List<VenueRecommendationVO> recommend(AiVenueRecommendRequest request);
}
