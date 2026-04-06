package com.courtflow.homework.mapping;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.courtflow.homework.entity.Venue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VenueMapper extends BaseMapper<Venue> {
}
