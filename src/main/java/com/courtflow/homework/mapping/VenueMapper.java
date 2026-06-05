package com.courtflow.homework.mapping;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.courtflow.homework.entity.Venue;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface VenueMapper extends BaseMapper<Venue> {
    @Select("""
            SELECT `id` AS venueId,
                   `name` AS venueName,
                   `type` AS venueType,
                   `status` AS venueStatus,
                   `created_at` AS venueCreatedAt
            FROM `venue`
            WHERE 1 = 1
            ORDER BY `id` ASC
            """)
    List<Map<String, Object>> selectAllForAdmin();
}
