package com.kuangshi.assets.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AssetSizeUtil {
    /**
     * 将带单位的文件大小转换为字节数
     * @param size 文件大小数值
     * @param unit 单位（KB、MB、GB等）
     * @return 字节数
     */
    public static Long convertSizeToBytes(Double size, String unit) {
        if (size == null || size <= 0) {
            return 0L;
        }

        if (unit == null || unit.isEmpty()) {
            return size.longValue();
        }

        return switch (unit.toUpperCase()) {
            case "B" -> size.longValue();
            case "KB" -> (long) (size * 1024);
            case "MB" -> (long) (size * 1024 * 1024);
            case "GB" -> (long) (size * 1024 * 1024 * 1024);
            case "TB" -> (long) (size * 1024L * 1024 * 1024 * 1024);
            default -> {
                log.warn("未知的文件大小单位: {}", unit);
                yield size.longValue();
            }
        };
    }
}
