package com.courtflow.homework.mapping;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.courtflow.homework.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {

}
