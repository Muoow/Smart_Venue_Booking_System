package com.courtflow.homework.mapping;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.courtflow.homework.entity.VenueResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface VenueResourceMapper extends BaseMapper<VenueResource> {
    @Select("""
            SELECT `id` AS resourceId,
                   `venue_id` AS venueId,
                   `name` AS resourceName,
                   `resource_type` AS resourceTypeCode,
                   `capacity` AS capacity,
                   `price` AS price,
                   `unit_minutes` AS unitMinutes,
                   `status` AS resourceStatus
            FROM `venue_resource`
            WHERE 1 = 1
            ORDER BY `venue_id` ASC, `id` ASC
            """)
    List<Map<String, Object>> selectAllForAdmin();
}
