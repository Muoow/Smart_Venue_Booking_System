package com.courtflow.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.courtflow.homework.common.enums.OrderStatusEnum;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Builder
@Data
@TableName("order")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long totalAmount;
    private OrderStatusEnum status;
    private Date expiredAt;
    private Date createdAt;
    private Date updatedAt;
}
