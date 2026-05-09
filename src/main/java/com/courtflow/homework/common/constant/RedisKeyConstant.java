package com.courtflow.homework.common.constant;

public class RedisKeyConstant {

    /**
     * 资源容量缓存key前缀
     * 根据容量来判断使用bitmap还是hash
     * - capacity = 1: bitmap:resourceId:date
     * - capacity > 1: hash:resourceId:date
     */
    public static final String CAPACITY_KEY_PREFIX = "capacity:";

    public static final String BITMAP_KEY_PREFIX = "bitmap:";

    public static final String HASH_KEY_PREFIX = "hash:";

    /**
     * 生成容量key
     * 格式: capacity:resourceId:yyyyMMdd
     */
    public static String getCapacityKey(Long resourceId, String dateString) {
        return CAPACITY_KEY_PREFIX + resourceId + ":" + dateString;
    }

    /**
     * 生成bitmap key
     * 格式: bitmap:resourceId:yyyyMMdd
     */
    public static String getBitmapKey(Long resourceId, String dateString) {
        return BITMAP_KEY_PREFIX + resourceId + ":" + dateString;
    }

    /**
     * 生成hash key
     * 格式: hash:resourceId:yyyyMMdd
     */
    public static String getHashKey(Long resourceId, String dateString) {
        return HASH_KEY_PREFIX + resourceId + ":" + dateString;
    }

    /**
     * 预约ID临时缓存key（用于异步处理）
     * 格式: reservation:temp:reservationId
     */
    public static String getReservationTempKey(Long reservationId) {
        return "reservation:temp:" + reservationId;
    }
}
