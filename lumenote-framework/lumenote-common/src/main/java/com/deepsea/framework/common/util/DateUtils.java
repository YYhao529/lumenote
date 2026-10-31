package com.deepsea.framework.common.util;

import com.deepsea.framework.common.constant.DateConstants;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 日期工具类
 */
public class DateUtils {

    /**
     * LocalDateTime 转时间戳
     */
    public static long localDateTime2Timestamp(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * LocalDateTime 转字符串
     * eg: 2024-06-07 16:06:47 -> "2024-06-07 16:06:47"
     */
    public static String localDateTime2String(LocalDateTime localDateTime) {
        return localDateTime.format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
    }

    /**
     * LocalDateTime 转友好的相对时间字符串
     */
    public static String formatRelativeTime(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        if (dateTime.isAfter(startOfDay)) {   // 如果为同一天，需要判断是 1 小时前还是 1 小时以内
            if (dateTime.isBefore(now.minusHours(1))) {   // 如果为 1 小时内前，格式化为 “1小时前”
                // 时间相减，获取小时数
                long hours = Duration.between(dateTime, now).toHours();
                return hours + "小时前";
            } else {  // 如果为 1 小时以内，格式化为 “10分钟前”
                // 时间相减，获取分钟数
                long minutes = Duration.between(dateTime, now).toMinutes();
                return minutes + "分钟前";
            }
        } else if (dateTime.isAfter(startOfDay.minusDays(1))) {   // 如果为昨天，格式化为 “昨天 20:12”
            String format = dateTime.format(DateConstants.DATE_FORMAT_H_M);
            return "昨天 " + format;
        } else if (dateTime.isAfter(startOfDay.minusDays(7))) {   // 如果为 7 天前，格式化为 “7天前”
            long days = Duration.between(dateTime, now).toDays();
            return days + "天前";
        } else {
            int updateYear = dateTime.getYear();
            int year = now.getYear();

            if (year == updateYear) {  // 一年以内，但是七天以前，格式化为具体日期“月-日”
                return dateTime.format(DateConstants.DATE_FORMAT_M_D);
            } else {  // 不是同一年，格式化为 “2023-01-01”
                return dateTime.format(DateConstants.DATE_FORMAT_Y_M_D);
            }
        }
    }

    public static void main(String[] args) {
        // 测试示例
        LocalDateTime dateTime1 = LocalDateTime.now().minusMinutes(10); // 10分钟前
        LocalDateTime dateTime2 = LocalDateTime.now().minusHours(3); // 3小时前
        LocalDateTime dateTime3 = LocalDateTime.now().minusDays(1).minusHours(5); // 昨天 20:12
        LocalDateTime dateTime4 = LocalDateTime.now().minusDays(2); // 2天前
        LocalDateTime dateTime5 = LocalDateTime.now().minusDays(10); // 11-06
        LocalDateTime dateTime6 = LocalDateTime.of(2023, 12, 1, 12, 30, 0); // 2023-12-01

        System.out.println(formatRelativeTime(dateTime1)); // 输出 "10分钟前"
        System.out.println(formatRelativeTime(dateTime2)); // 输出 "3小时前"
        System.out.println(formatRelativeTime(dateTime3)); // 输出 "昨天 20:12"
        System.out.println(formatRelativeTime(dateTime4)); // 输出 "2天前"
        System.out.println(formatRelativeTime(dateTime5)); // 输出 "11-06"
        System.out.println(formatRelativeTime(dateTime6)); // 输出 "2023-12-01"
    }
}
