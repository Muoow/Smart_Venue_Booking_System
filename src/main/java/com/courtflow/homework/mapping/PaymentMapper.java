package com.courtflow.homework.mapping;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.courtflow.homework.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
