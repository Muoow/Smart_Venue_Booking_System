package com.courtflow.homework.common.util;

import java.time.*;
import java.util.Date;

public class DateUtil {

    /**
     * 计算到当天23:59:59的剩余秒数（用于Redis TTL）
     *
     * @param date 指定日期
     * @return 剩余秒数
     */
    public static long getTtlSecondsToEndOfDay(Date date) {
        Instant instant = date.toInstant();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate localDate = instant.atZone(zoneId).toLocalDate();
        LocalDateTime endOfDay = localDate.atTime(23, 59, 59);
        ZonedDateTime zonedDateTime = endOfDay.atZone(zoneId);
        Instant endInstant = zonedDateTime.toInstant();

        long remainingSeconds = (endInstant.toEpochMilli() - date.getTime()) / 1000;
        return Math.max(remainingSeconds, 1); // 至少返回1秒
    }

    /**
     * 获取日期的字符串表示（格式：yyyyMMdd）
     *
     * @param date 日期
     * @return 日期字符串
     */
    public static String getDateString(Date date) {
        Instant instant = date.toInstant();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate localDate = instant.atZone(zoneId).toLocalDate();
        return String.format("%04d%02d%02d", localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
    }

    /**
     * 将Date转换为LocalDate
     */
    public static LocalDate toLocalDate(Date date) {
        Instant instant = date.toInstant();
        ZoneId zoneId = ZoneId.systemDefault();
        return instant.atZone(zoneId).toLocalDate();
    }
}
