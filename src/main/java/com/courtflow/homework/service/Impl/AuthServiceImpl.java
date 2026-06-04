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
    public void register(String username, String nickname, String password) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedNickname = normalizeNickname(nickname);
        validatePassword(password);

        LambdaQueryWrapper<UserAuth> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserAuth::getIdentityType, "username")
                .eq(UserAuth::getIdentifier, normalizedUsername);

        if (authMapper.selectOne(queryWrapper) != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已存在。");
        }

        User user = User.builder()
                .username(normalizedUsername)
                .nickname(normalizedNickname)
                .fullName(normalizedNickname)
                .status(UserStatusEnum.ENABLED)
                .balance(0L)
                .role("USER")
                .build();

        userMapper.insert(user);

        String encodedPassword = passwordEncoder.encode(password);

        UserAuth userAuth = UserAuth.builder()
                .userId(user.getId())
                .identityType("username")
                .identifier(normalizedUsername)
                .credential(encodedPassword)
                .build();

        authMapper.insert(userAuth);
    }

    @Override
    public String login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        if (password == null || password.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码不能为空。");
        }

        LambdaQueryWrapper<UserAuth> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserAuth::getIdentityType, "username")
                .eq(UserAuth::getIdentifier, normalizedUsername);

        UserAuth userAuth = authMapper.selectOne(queryWrapper);

        if (userAuth == null || !passwordEncoder.matches(password, userAuth.getCredential())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误。");
        }

        User user = userMapper.selectById(userAuth.getUserId());

        if (user == null || user.getStatus() == UserStatusEnum.DISABLED) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前账号已被禁用。");
        }

        userAuth.setLastLoginAt(System.currentTimeMillis());
        userAuth.setUpdatedAt(new Date());
        authMapper.updateById(userAuth);

        return jwtUtils.generateToken(user);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名不能为空。");
        }
        String normalized = username.trim();
        if (normalized.length() < 3 || normalized.length() > 32) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名长度需在 3 到 32 位之间。");
        }
        return normalized;
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "昵称不能为空。");
        }
        String normalized = nickname.trim();
        if (normalized.length() < 2 || normalized.length() > 32) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "昵称长度需在 2 到 32 位之间。");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码不能为空。");
        }
        if (password.length() < 5 || password.length() > 64) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码长度需在 5 到 64 位之间。");
        }
    }
}
