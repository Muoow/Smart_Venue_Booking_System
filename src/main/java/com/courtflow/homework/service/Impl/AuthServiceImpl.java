package com.courtflow.homework.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.common.dto.response.ResultCode;
import com.courtflow.homework.common.enums.UserStatusEnum;
import com.courtflow.homework.entity.User;
import com.courtflow.homework.entity.UserAuth;
import com.courtflow.homework.mapping.UserAuthMapper;
import com.courtflow.homework.mapping.UserMapper;
import com.courtflow.homework.service.AuthService;
import com.courtflow.homework.common.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    private final UserAuthMapper authMapper;

    private final JwtUtils jwtUtils;

    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AuthServiceImpl(UserMapper userMapper, UserAuthMapper authMapper, JwtUtils jwtUtils) {
        this.userMapper = userMapper;
        this.authMapper = authMapper;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional
    @Override
    public void register(String username, String password) {

        LambdaQueryWrapper<UserAuth> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserAuth::getIdentityType, "username")
                .eq(UserAuth::getIdentifier, username);

        if (authMapper.selectOne(queryWrapper) != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Username already exists.");
        }

        User user = User.builder()
                .username(username)
                .status(UserStatusEnum.ENABLED)
                .balance(0L)
                .role("USER")
                .build();

        userMapper.insert(user);

        String encodedPassword = passwordEncoder.encode(password);

        UserAuth userAuth = UserAuth.builder()
                .userId(user.getId())
                .identityType("username")
                .identifier(username)
                .credential(encodedPassword)
                .build();

        authMapper.insert(userAuth);
    }

    @Override
    public String login(String username, String password) {

        LambdaQueryWrapper<UserAuth> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserAuth::getIdentityType, "username")
                .eq(UserAuth::getIdentifier, username);

        UserAuth userAuth = authMapper.selectOne(queryWrapper);

        if (userAuth == null || !passwordEncoder.matches(password, userAuth.getCredential())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Username or password incorrect.");
        }

        User user = userMapper.selectById(userAuth.getUserId());

        if (user == null || user.getStatus() == UserStatusEnum.DISABLED) {
            throw new BusinessException(ResultCode.FORBIDDEN, "User has disabled.");
        }

        userAuth.setLastLoginAt(System.currentTimeMillis());
        userAuth.setUpdatedAt(new Date());
        authMapper.updateById(userAuth);

        return jwtUtils.generateToken(user);
    }
}
