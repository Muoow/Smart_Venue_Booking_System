package com.courtflow.homework.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.courtflow.homework.common.context.UserContext;
import com.courtflow.homework.common.dto.request.UpdatePasswordRequest;
import com.courtflow.homework.common.dto.request.UpdateUserProfileRequest;
import com.courtflow.homework.common.dto.response.ApiResponse;
import com.courtflow.homework.common.dto.response.ResultCode;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.entity.User;
import com.courtflow.homework.entity.UserAuth;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.UserAuthMapper;
import com.courtflow.homework.mapping.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserMapper userMapper;

    private final UserAuthMapper userAuthMapper;

    private final ReservationMapper reservationMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserController(UserMapper userMapper, UserAuthMapper userAuthMapper, ReservationMapper reservationMapper) {
        this.userMapper = userMapper;
        this.userAuthMapper = userAuthMapper;
        this.reservationMapper = reservationMapper;
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        User user = requireCurrentUser();
        return ApiResponse.success(buildProfile(user));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody UpdateUserProfileRequest request) {
        User user = requireCurrentUser();

        String nickname = normalizeProfileField(request == null ? null : request.getNickname(), "昵称");
        if (nickname == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "昵称不能为空。");
        }

        user.setNickname(nickname);
        user.setFullName(nickname);
        userMapper.updateById(user);
        return ApiResponse.success(buildProfile(userMapper.selectById(user.getId())));
    }

    @PutMapping("/password")
    public ApiResponse<Map<String, Object>> updatePassword(@RequestBody UpdatePasswordRequest request) {
        User user = requireCurrentUser();
        String currentPassword = request == null ? null : request.getCurrentPassword();
        String newPassword = request == null ? null : request.getNewPassword();

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前密码不能为空。");
        }
        validatePassword(newPassword);

        UserAuth userAuth = userAuthMapper.selectOne(
                Wrappers.<UserAuth>lambdaQuery()
                        .eq(UserAuth::getUserId, user.getId())
                        .eq(UserAuth::getIdentityType, "username")
                        .last("LIMIT 1")
        );
        if (userAuth == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户登录凭证不存在。");
        }

        boolean currentPasswordMatched = passwordEncoder.matches(currentPassword, userAuth.getCredential());
        if (!currentPasswordMatched) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前密码不正确。");
        }
        if (passwordEncoder.matches(newPassword, userAuth.getCredential())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能与当前密码相同。");
        }

        userAuth.setCredential(passwordEncoder.encode(newPassword));
        userAuth.setUpdatedAt(new Date());
        userAuthMapper.updateById(userAuth);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", true);
        return ApiResponse.success(result);
    }

    private User requireCurrentUser() {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录。");
        }

        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在。");
        }
        return user;
    }

    private Map<String, Object> buildProfile(User user) {
        Long currentUserId = user.getId();
        long totalReservations = reservationMapper.selectCount(
                Wrappers.<com.courtflow.homework.entity.Reservation>lambdaQuery()
                        .eq(com.courtflow.homework.entity.Reservation::getUserId, currentUserId)
        );
        long activeReservations = reservationMapper.selectCount(
                Wrappers.<com.courtflow.homework.entity.Reservation>lambdaQuery()
                        .eq(com.courtflow.homework.entity.Reservation::getUserId, currentUserId)
                        .in(
                                com.courtflow.homework.entity.Reservation::getStatus,
                                ReservationStatusEnum.QUEUING,
                                ReservationStatusEnum.RESERVED
                        )
        );
        long cancelledReservations = reservationMapper.selectCount(
                Wrappers.<com.courtflow.homework.entity.Reservation>lambdaQuery()
                        .eq(com.courtflow.homework.entity.Reservation::getUserId, currentUserId)
                        .eq(com.courtflow.homework.entity.Reservation::getStatus, ReservationStatusEnum.CANCELLED)
        );

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("nickname", emptyToNull(user.getNickname()));
        profile.put("fullName", emptyToNull(user.getFullName()));
        profile.put("role", user.getRole());
        profile.put("balance", user.getBalance());
        profile.put("totalReservations", totalReservations);
        profile.put("activeReservations", activeReservations);
        profile.put("cancelledReservations", cancelledReservations);
        return profile;
    }

    private String normalizeProfileField(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 32) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + "长度不能超过 32 个字符。");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能为空。");
        }
        if (password.length() < 5 || password.length() > 64) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码长度需在 5 到 64 位之间。");
        }
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
