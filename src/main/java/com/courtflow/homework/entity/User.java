package com.courtflow.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.courtflow.homework.common.enums.UserStatusEnum;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Builder
@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private UserStatusEnum status;
    private Date createdAt;
    private Long balance;
    private String role;
}
