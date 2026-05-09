package com.courtflow.homework.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.courtflow.homework.common.constant.RedisKeyConstant;
import com.courtflow.homework.common.dto.request.PageQueryRequest;
import com.courtflow.homework.common.dto.request.ReservationApplyRequest;
import com.courtflow.homework.common.dto.response.ResultCode;
import com.courtflow.homework.common.enums.ReservationStatusEnum;
import com.courtflow.homework.common.exception.BusinessException;
import com.courtflow.homework.common.util.DateUtil;
import com.courtflow.homework.common.util.QueuePartitionUtil;
import com.courtflow.homework.common.util.RedisScriptUtil;
import com.courtflow.homework.common.vo.ReservationVO;
import com.courtflow.homework.entity.Reservation;
import com.courtflow.homework.entity.VenueResource;
import com.courtflow.homework.mapping.OrderMapper;
import com.courtflow.homework.mapping.ReservationMapper;
import com.courtflow.homework.mapping.TimeSlotMapper;
import com.courtflow.homework.mapping.VenueResourceMapper;
import com.courtflow.homework.service.ReservationService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class ReservationServiceImpl implements ReservationService {

    private final ReservationMapper reservationMapper;

    private final TimeSlotMapper timeSlotMapper;

    private final OrderMapper orderMapper;

    private final VenueResourceMapper venueResourceMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RedisScriptUtil redisScriptUtil;

    @Autowired
    public ReservationServiceImpl(ReservationMapper reservationMapper, TimeSlotMapper timeSlotMapper,
                                 OrderMapper orderMapper, VenueResourceMapper venueResourceMapper) {
        this.reservationMapper = reservationMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.orderMapper = orderMapper;
        this.venueResourceMapper = venueResourceMapper;
    }

    @Override
    public Long apply(ReservationApplyRequest request) {
        // 1. 参数验证
        if (request.getUserId() == null || request.getResourceId() == null ||
            request.getSlotDate() == null || request.getStartUnit() == null ||
            request.getEndUnit() == null || request.getSize() == null || request.getSize() <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Invalid reservation request parameters");
        }

        if (request.getEndUnit() < request.getStartUnit()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "endUnit must be >= startUnit");
        }

        // 2. 获取资源信息
        VenueResource resource = venueResourceMapper.selectById(request.getResourceId());
        if (resource == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Resource not found");
        }

        // 3. 检查请求大小是否超过资源容量
        if (request.getSize() > resource.getCapacity()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "Requested size exceeds resource capacity");
        }

        // 4. 获取Redis key和参数
        String dateString = DateUtil.getDateString(request.getSlotDate());
        long ttlSeconds = DateUtil.getTtlSecondsToEndOfDay(request.getSlotDate());

        // 5. 执行预扣库存
        boolean reservationSuccess = false;
        String redisKey;

        if (resource.getCapacity() == 1) {
            // 使用bitmap方式
            redisKey = RedisKeyConstant.getBitmapKey(request.getResourceId(), dateString);
            List result = redisScriptUtil.executeForBitmap(
                    redisKey,
                    request.getStartUnit(),
                    request.getEndUnit(),
                    ttlSeconds
            );

            if (result != null && !result.isEmpty()) {
                Object firstElement = result.get(0);
                if (firstElement instanceof Number) {
                    reservationSuccess = ((Number) firstElement).intValue() == 1;
                } else if (firstElement instanceof Long) {
                    reservationSuccess = ((Long) firstElement) == 1L;
                }
            }
        } else {
            // 使用hash方式
            redisKey = RedisKeyConstant.getHashKey(request.getResourceId(), dateString);
            List result = redisScriptUtil.executeForHash(
                    redisKey,
                    request.getStartUnit(),
                    request.getEndUnit(),
                    resource.getCapacity(),
                    request.getSize(),
                    ttlSeconds
            );

            if (result != null && !result.isEmpty()) {
                Object firstElement = result.get(0);
                if (firstElement instanceof Number) {
                    reservationSuccess = ((Number) firstElement).intValue() == 1;
                } else if (firstElement instanceof Long) {
                    reservationSuccess = ((Long) firstElement) == 1L;
                }
            }
        }

        if (!reservationSuccess) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "Time slot is not available");
        }

        // 6. 创建Reservation记录
        Reservation reservation = Reservation.builder()
                .userId(request.getUserId())
                .venueId(request.getVenueId())
                .resourceId(request.getResourceId())
                .slotDate(request.getSlotDate())
                .startUnit(request.getStartUnit())
                .endUnit(request.getEndUnit())
                .size(request.getSize())
                .status(ReservationStatusEnum.QUEUING)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        reservationMapper.insert(reservation);
        Long reservationId = reservation.getId();

        // 7. 发送消息到队列
        int partition = QueuePartitionUtil.getPartition(request.getResourceId(), request.getSlotDate().getTime());
        String queueName = QueuePartitionUtil.getQueueName(partition);

        try {
            // 消息体是8字节的长整数（reservationId）
            byte[] messageBody = ByteBuffer.allocate(8).putLong(reservationId).array();
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_BYTES);
            Message message = new Message(messageBody, messageProperties);
            rabbitTemplate.send(queueName, message);

            log.info("Sent reservation message. reservationId={}, queueName={}, partition={}",
                     reservationId, queueName, partition);
        } catch (Exception e) {
            log.error("Failed to send reservation message. reservationId={}, queueName={}",
                      reservationId, queueName, e);
            // 消息发送失败，但预扣库存已成功，标记为QUEUING状态等待重试
        }

        return reservationId;
    }

    @Override
    public Boolean cancel(Long id) {
        // 1. 获取预约记录
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new IllegalArgumentException("Reservation not found");
        }

        // 2. 只允许取消处于QUEUING状态的预约
        if (!ReservationStatusEnum.QUEUING.equals(reservation.getStatus())) {
            log.warn("Cannot cancel reservation not in QUEUING status. reservationId={}, status={}",
                     id, reservation.getStatus());
            return false;
        }

        // 3. 获取资源信息
        VenueResource resource = venueResourceMapper.selectById(reservation.getResourceId());
        if (resource == null) {
            log.warn("Resource not found for cancellation. resourceId={}", reservation.getResourceId());
            return false;
        }

        // 4. 恢复Redis库存
        String dateString = DateUtil.getDateString(reservation.getSlotDate());
        long ttlSeconds = DateUtil.getTtlSecondsToEndOfDay(reservation.getSlotDate());

        try {
            if (resource.getCapacity() == 1) {
                // 对于bitmap: 将占用的位恢复为0
                String redisKey = RedisKeyConstant.getBitmapKey(reservation.getResourceId(), dateString);
                for (int unit = reservation.getStartUnit(); unit <= reservation.getEndUnit(); unit++) {
                    redisTemplate.opsForValue().setBit(redisKey, unit, false);
                }
                // 续期TTL
                redisTemplate.expire(redisKey, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            } else {
                // 对于hash: 增加已占用的数量
                String redisKey = RedisKeyConstant.getHashKey(reservation.getResourceId(), dateString);
                for (int unit = reservation.getStartUnit(); unit <= reservation.getEndUnit(); unit++) {
                    String field = "slot_" + unit;
                    redisTemplate.opsForHash().increment(redisKey, field, reservation.getSize());
                }
                // 续期TTL
                redisTemplate.expire(redisKey, ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("Failed to recover Redis inventory during cancellation. reservationId={}", id, e);
            // 继续处理，库存恢复失败不应该影响取消操作
        }

        // 5. 更新Reservation状态为CANCELLED
        reservation.setStatus(ReservationStatusEnum.CANCELLED);
        reservation.setUpdatedAt(new Date());
        reservationMapper.updateById(reservation);

        log.info("Reservation cancelled successfully. reservationId={}", id);
        return true;
    }

    @Override
    public ReservationVO getById(Long id) {
        Reservation reservation = reservationMapper.selectById(id);

        if (reservation == null) {
            return null;
        }

        return convertToVO(reservation);
    }

    @Override
    public IPage<ReservationVO> getByUserId(Long userId, PageQueryRequest request) {
        Page<Reservation> page = new Page<>(
                request.getPageNumber(),
                request.getPageSize()
        );

        LambdaQueryWrapper<Reservation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getCreatedAt);

        IPage<Reservation> result = reservationMapper.selectPage(page, wrapper);

        return result.convert(this::convertToVO);
    }

    private ReservationVO convertToVO(Reservation reservation) {
        return ReservationVO.builder()
                .id(reservation.getId())
                .venueId(reservation.getVenueId())
                .resourceId(reservation.getResourceId())
                .slotDate(reservation.getSlotDate())
                .startUnit(reservation.getStartUnit())
                .endUnit(reservation.getEndUnit())
                .size(reservation.getSize())
                .status(reservation.getStatus())
                .build();
    }
}
