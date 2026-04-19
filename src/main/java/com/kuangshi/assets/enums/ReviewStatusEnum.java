package com.kuangshi.assets.enums;

import lombok.Getter;

/**
 * 素材审核状态枚举
 */
@Getter
public enum ReviewStatusEnum {

    /**
     * 待审核
     */
    PENDING("pending", "待审核"),

    /**
     * 已通过
     */
    APPROVED("approved", "已通过"),

    /**
     * 已拒绝
     */
    REJECTED("rejected", "已拒绝");

    /**
     * 状态码
     */
    private final String code;

    /**
     * 状态描述
     */
    private final String description;

    ReviewStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据状态码获取枚举
     *
     * @param code 状态码
     * @return 枚举对象，如果不存在返回 null
     */
    public static ReviewStatusEnum fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (ReviewStatusEnum status : ReviewStatusEnum.values()) {
            if (status.getCode().toLowerCase().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据描述获取枚举
     *
     * @param description 状态描述
     * @return 枚举对象，如果不存在返回 null
     */
    public static ReviewStatusEnum fromDescription(String description) {
        if (description == null || description.isEmpty()) {
            return null;
        }
        for (ReviewStatusEnum status : ReviewStatusEnum.values()) {
            if (status.getDescription().equals(description)) {
                return status;
            }
        }
        return null;
    }
}