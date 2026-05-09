package com.courtflow.homework.common.util;

import com.courtflow.homework.common.constant.MqConstant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class QueuePartitionUtil {

    /**
     * 根据资源ID和日期计算队列分区
     *
     * @param resourceId 资源ID
     * @param slotDateTimeMillis 日期时间戳（毫秒）
     * @return 分区索引 [0, QUEUE_PARTITIONS)
     */
    public static int getPartition(Long resourceId, Long slotDateTimeMillis) {
        String input = resourceId + "-" + slotDateTimeMillis;
        int hash = hashCode(input);
        return Math.abs(hash) % MqConstant.QUEUE_PARTITIONS;
    }

    /**
     * 获取对应分区的队列名称
     *
     * @param partition 分区索引
     * @return 队列名称
     */
    public static String getQueueName(int partition) {
        return MqConstant.RESERVATION_QUEUE + partition;
    }

    /**
     * 获取对应分区的routing key
     *
     * @param partition 分区索引
     * @return routing key
     */
    public static String getRoutingKey(int partition) {
        return MqConstant.RESERVATION_ROUTING_KEY + partition;
    }

    /**
     * 计算hash值 (使用MD5提高分布均匀性)
     */
    private static int hashCode(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            int hash = 0;
            for (byte b : messageDigest) {
                hash = (hash << 5) - hash + (b & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }
}
