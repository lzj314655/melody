package com.melody.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 商品评价相关 DTO（F-U-061 ~ F-U-064）
 */
public class ReviewDTOs {

    /** 提交评价请求 */
    @Data
    public static class ReviewSubmitRequest {
        @NotNull
        private Long productId;

        /** 1-5 星；前端 5 星组件天然限范围，后端兜底 */
        @NotNull
        @Min(1)
        @Max(5)
        private Integer rating;

        @NotBlank
        @Size(max = 500)
        private String content;
    }

    /** 单条评价展示项（不直接返回 entity，避免泄露 userId 等敏感字段） */
    @Data
    public static class ReviewListItemVO {
        private Long id;
        private Integer rating;
        private String content;
        /** 显示名：匿名时为"匿名用户"，否则为用户昵称 */
        private String displayName;
        /** 是否匿名 */
        private Boolean anonymous;
        /** 头像 URL，仅非匿名时返回 */
        private String avatar;
        /** 创建时间 ISO 字符串 */
        private String createdAt;
    }

    /** 商品评价分页 VO（含统计信息） */
    @Data
    public static class ReviewPageVO {
        /** 总条数 */
        private Long total;
        /** 5 星占比 0-1，前端展示"99.8% 好评" */
        private Double positiveRate;
        /** 平均分，保留 1 位小数 */
        private Double averageRating;
        /** 当前页数据 */
        private List<ReviewListItemVO> items;
    }

    /** 评价资格预检（点击"我要评价"前调） */
    @Data
    public static class ReviewEligibilityVO {
        /** 能否提交评价 */
        private Boolean canReview;
        /**
         * 不可评的原因枚举：
         *  - NOT_LOGGED_IN  未登录
         *  - NOT_PURCHASED  未购买
         *  - ALREADY_REVIEWED 已评过
         */
        private String reason;
        /** 当 ALREADY_REVIEWED 时，返回已有评价 id 供前端跳转到我的评价 */
        private Long existingReviewId;
    }
}
