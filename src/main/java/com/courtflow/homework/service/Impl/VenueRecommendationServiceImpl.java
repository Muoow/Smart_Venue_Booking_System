package com.courtflow.homework.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.courtflow.homework.common.dto.request.AiVenueRecommendRequest;
import com.courtflow.homework.common.enums.ResourceStatusEnum;
import com.courtflow.homework.common.enums.ResourceTypeEnum;
import com.courtflow.homework.common.vo.VenueRecommendationVO;
import com.courtflow.homework.entity.Venue;
import com.courtflow.homework.entity.VenueResource;
import com.courtflow.homework.mapping.VenueMapper;
import com.courtflow.homework.mapping.VenueResourceMapper;
import com.courtflow.homework.service.VenueRecommendationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VenueRecommendationServiceImpl implements VenueRecommendationService {

    private final VenueMapper venueMapper;

    private final VenueResourceMapper venueResourceMapper;

    public VenueRecommendationServiceImpl(VenueMapper venueMapper, VenueResourceMapper venueResourceMapper) {
        this.venueMapper = venueMapper;
        this.venueResourceMapper = venueResourceMapper;
    }

    @Override
    public List<VenueRecommendationVO> recommend(AiVenueRecommendRequest request) {
        List<Venue> venueList = venueMapper.selectList(
                Wrappers.<Venue>lambdaQuery().eq(Venue::getStatus, 1)
        );
        Map<Long, Venue> venueMap = venueList.stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));

        List<VenueResource> resourceList = venueResourceMapper.selectList(
                Wrappers.<VenueResource>lambdaQuery().eq(VenueResource::getStatus, ResourceStatusEnum.ENABLED)
        );

        return resourceList.stream()
                .filter(resource -> venueMap.containsKey(resource.getVenueId()))
                .map(resource -> buildRecommendation(request, venueMap.get(resource.getVenueId()), resource))
                .sorted(Comparator.comparing(VenueRecommendationVO::getScore).reversed())
                .limit(resolveTopN(request))
                .toList();
    }

    private VenueRecommendationVO buildRecommendation(
            AiVenueRecommendRequest request,
            Venue venue,
            VenueResource resource
    ) {
        double score = 50D;
        List<String> reasonList = new ArrayList<>();

        if (matchesSportKeyword(request.getSportKeyword(), resource.getResourceType(), venue.getType())) {
            score += 22D;
            reasonList.add("场地类型与用户运动偏好高度匹配");
        }

        if (request.getPreferredUnitMinutes() != null && resource.getUnitMinutes() != null) {
            if (resource.getUnitMinutes() <= request.getPreferredUnitMinutes()) {
                score += 12D;
                reasonList.add("时间片粒度更细，适合分钟级灵活预约");
            } else {
                score -= 4D;
            }
        }

        if (request.getExpectedPeopleCount() != null && resource.getCapacity() != null) {
            if (resource.getCapacity() >= request.getExpectedPeopleCount()) {
                score += 10D;
                reasonList.add("资源容量满足本次组局人数需求");
            } else {
                score -= 12D;
                reasonList.add("资源容量偏小，不适合当前人数规模");
            }
        }

        if (request.getMaxBudget() != null && resource.getPrice() != null) {
            if (resource.getPrice() <= request.getMaxBudget()) {
                score += 10D;
                reasonList.add("价格落在用户预算范围内");
            } else {
                score -= 8D;
            }
        }

        if (Boolean.TRUE.equals(request.getPreferLowPrice()) && resource.getPrice() != null) {
            score += Math.max(0D, 15D - resource.getPrice() / 10.0D);
            reasonList.add("推荐模型对低价资源进行了加权");
        }

        if (Boolean.TRUE.equals(request.getPreferLargeCapacity()) && resource.getCapacity() != null) {
            score += Math.min(10D, resource.getCapacity() * 1.0D);
            reasonList.add("推荐模型优先考虑承载能力更高的场地");
        }

        if (request.getExpectedStartUnit() != null && request.getExpectedEndUnit() != null) {
            int duration = request.getExpectedEndUnit() - request.getExpectedStartUnit() + 1;
            if (duration > 0) {
                score += Math.min(6D, duration / 2.0D);
                reasonList.add("已综合考虑用户期望的预约时长与连续时间片需求");
            }
        }

        if (reasonList.isEmpty()) {
            reasonList.add("当前推荐结果基于基础画像与资源属性进行排序");
        }

        return VenueRecommendationVO.builder()
                .venueId(venue.getId())
                .venueName(venue.getName())
                .resourceId(resource.getId())
                .resourceName(resource.getName())
                .estimatedPrice(resource.getPrice())
                .unitMinutes(resource.getUnitMinutes())
                .capacity(resource.getCapacity())
                .score(Math.round(score * 10D) / 10D)
                .reasonList(reasonList)
                .build();
    }

    private boolean matchesSportKeyword(String keyword, ResourceTypeEnum resourceType, String venueType) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }

        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        String resourceName = resourceType == null ? "" : resourceType.name().toLowerCase(Locale.ROOT);
        String resourceDesc = resourceType == null ? "" : resourceType.getDesc();
        String normalizedVenueType = Objects.toString(venueType, "").toLowerCase(Locale.ROOT);

        return resourceName.contains(normalizedKeyword)
                || resourceDesc.contains(keyword)
                || normalizedVenueType.contains(normalizedKeyword);
    }

    private int resolveTopN(AiVenueRecommendRequest request) {
        if (request.getTopN() == null || request.getTopN() <= 0) {
            return 3;
        }
        return Math.min(request.getTopN(), 10);
    }
}
