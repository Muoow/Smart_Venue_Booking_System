package com.courtflow.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.courtflow.homework.common.enums.PaymentBizTypeEnum;
import com.courtflow.homework.common.enums.PaymentStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("`payment`")
public class Payment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String paymentNo;
    private PaymentBizTypeEnum bizType;
    private Integer payChannel;
    private String channelTradeNo;
    private Long payAmount;
    private PaymentStatusEnum payStatus;
    private String statusNote;
    private Date paidAt;
    private Date processedAt;
    private Date createdAt;
}
