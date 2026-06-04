package com.courtflow.homework.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.courtflow.homework.common.annonation.CheckToken;
import com.courtflow.homework.common.dto.response.ApiResponse;
import com.courtflow.homework.common.enums.ResourceStatusEnum;
import com.courtflow.homework.entity.Venue;
import com.courtflow.homework.entity.VenueResource;
import com.courtflow.homework.mapping.VenueMapper;
import com.courtflow.homework.mapping.VenueResourceMapper;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CheckToken(required = false)
@RequestMapping("/venue")
public class VenueController {

    private final VenueMapper venueMapper;

    private final VenueResourceMapper venueResourceMapper;

    public VenueController(VenueMapper venueMapper, VenueResourceMapper venueResourceMapper) {
        this.venueMapper = venueMapper;
        this.venueResourceMapper = venueResourceMapper;
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Venue> venueList = venueMapper.selectList(
                Wrappers.<Venue>lambdaQuery()
                        .eq(Venue::getStatus, 1)
                        .orderByAsc(Venue::getId)
        );
        Map<Long, List<VenueResource>> resourcesByVenue = venueResourceMapper.selectList(
                        Wrappers.<VenueResource>lambdaQuery()
                                .eq(VenueResource::getStatus, ResourceStatusEnum.ENABLED)
                                .orderByAsc(VenueResource::getVenueId)
                                .orderByAsc(VenueResource::getId)
                ).stream()
                .collect(Collectors.groupingBy(VenueResource::getVenueId));

        List<Map<String, Object>> data = venueList.stream()
                .map(venue -> {
                    List<Map<String, Object>> resources = resourcesByVenue.getOrDefault(venue.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(VenueResource::getId))
                            .map(resource -> {
                                Map<String, Object> resourceData = new LinkedHashMap<>();
                                resourceData.put("id", resource.getId());
                                resourceData.put("name", resource.getName());
                                resourceData.put("resourceType", resource.getResourceType());
                                resourceData.put("capacity", resource.getCapacity());
                                resourceData.put("price", resource.getPrice());
                                resourceData.put("unitMinutes", resource.getUnitMinutes());
                                resourceData.put("status", resource.getStatus());
                                return resourceData;
                            })
                            .toList();

                    Map<String, Object> venueData = new LinkedHashMap<>();
                    venueData.put("id", venue.getId());
                    venueData.put("name", venue.getName());
                    venueData.put("type", venue.getType());
                    venueData.put("resourceCount", resources.size());
                    venueData.put("resources", resources);
                    return venueData;
                })
                .collect(Collectors.toList());

        return ApiResponse.success(data);
    }
}
