package com.courtflow.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_auth")
public class UserAuth {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String identityType;
    private String identifier;
    private String credential;
    private Long lastLoginAt;
    private Date createdAt;
    private Date updatedAt;
}
