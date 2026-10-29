package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.melody.common.ResultCode;
import com.melody.dto.ReviewDTOs.ReviewEligibilityVO;
import com.melody.dto.ReviewDTOs.ReviewListItemVO;
import com.melody.dto.ReviewDTOs.ReviewPageVO;
import com.melody.dto.ReviewDTOs.ReviewSubmitRequest;
import com.melody.entity.TProductReview;
import com.melody.entity.TUser;
import com.melody.exception.BizException;
import com.melody.mapper.TOrderMapper;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.TProductReviewMapper;
import com.melody.mapper.TUserMapper;
import com.melody.service.IReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品评价服务实现（F-U-061 评价列表 / F-U-062 评价提交）
 *
 * 关键业务规则：
 *  - 已购校验：TOrder 状态 ∈ {PENDING_RECEIVE, COMPLETED}
 *  - 防重：同一 (userId, productId) 仅允许一条（应用层 SELECT 校验，不依赖 DB 唯一约束）
 *  - 隐藏过滤：列表只返回 is_hidden = 0
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements IReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewServiceImpl.class);

    private final TProductReviewMapper reviewMapper;
    private final TUserMapper userMapper;
    private final TOrderMapper orderMapper;
    private final TProductMapper productMapper;

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 资格原因枚举（与前端 ReviewEligibilityVO.reason 对齐） */
    public static final String REASON_NOT_LOGGED_IN   = "NOT_LOGGED_IN";
    public static final String REASON_NOT_PURCHASED   = "NOT_PURCHASED";
    public static final String REASON_ALREADY_REVIEWED = "ALREADY_REVIEWED";

    // ============================ 列表 ============================

    @Override
    public ReviewPageVO listReviews(Long productId, int page, int size) {
        if (productId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "商品 ID 不能为空");
        }
        // 参数归一化：保护 DB / 防止 -1 等异常值
        if (page < 1) page = 1;
        if (size < 1) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;

        // 1) 统计：好评率 + 平均分（一条 SQL 全部拿回，避开分页导致的偏差）
        ReviewPageVO vo = new ReviewPageVO();
        Map<String, Object> stats = fetchRatingStats(productId);
        long total = toLong(stats.get("totalCount"));
        vo.setTotal(total);

        if (total == 0) {
            vo.setPositiveRate(0.0);
            vo.setAverageRating(0.0);
            vo.setItems(Collections.emptyList());
            return vo;
        }

        long positive = toLong(stats.get("positiveCount"));
        double avg = stats.get("avgRating") == null ? 0.0 : toDouble(stats.get("avgRating"));
        vo.setPositiveRate(round4((double) positive / total));
        vo.setAverageRating(round1(avg));

        // 2) 分页查记录
        Page<TProductReview> mpPage = new Page<>(page, size);
        LambdaQueryWrapper<TProductReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TProductReview::getProductId, productId)
               .eq(TProductReview::getIsHidden, 0)
               .orderByDesc(TProductReview::getCreatedAt);
        Page<TProductReview> result = reviewMapper.selectPage(mpPage, wrapper);
        List<TProductReview> records = result.getRecords();

        // 3) 批量查用户（仅查非匿名）
        Set<Long> userIds = records.stream()
                .filter(r -> r.getIsAnonymous() == null || r.getIsAnonymous() == 0)
                .map(TProductReview::getUserId)
                .collect(Collectors.toSet());
        Map<Long, TUser> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(TUser::getId, Function.identity(), (a, b) -> a));

        // 4) 装配 VO
        vo.setItems(records.stream().map(r -> toVO(r, userMap)).collect(Collectors.toList()));
        return vo;
    }

    // ============================ 资格预检 ============================

    @Override
    public ReviewEligibilityVO checkEligibility(Long userId, Long productId) {
        if (productId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "商品 ID 不能为空");
        }
        ReviewEligibilityVO vo = new ReviewEligibilityVO();
        if (userId == null) {
            vo.setCanReview(false);
            vo.setReason(REASON_NOT_LOGGED_IN);
            return vo;
        }
        // 防重（应用层查，避开 schema 加 unique index 的额外迁移）
        TProductReview existed = reviewMapper.selectOne(
                new LambdaQueryWrapper<TProductReview>()
                        .eq(TProductReview::getUserId, userId)
                        .eq(TProductReview::getProductId, productId)
                        .last("LIMIT 1"));
        if (existed != null) {
            vo.setCanReview(false);
            vo.setReason(REASON_ALREADY_REVIEWED);
            vo.setExistingReviewId(existed.getId());
            return vo;
        }
        // 已购校验：JOIN 订单表
        long purchased = orderMapper.countPurchasedProduct(userId, productId);
        if (purchased == 0) {
            vo.setCanReview(false);
            vo.setReason(REASON_NOT_PURCHASED);
            return vo;
        }
        vo.setCanReview(true);
        return vo;
    }

    // ============================ 提交 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitReview(Long userId, ReviewSubmitRequest req) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (req == null || req.getProductId() == null
                || req.getRating() == null || req.getContent() == null || req.getContent().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "评价参数不完整");
        }
        // 服务层再校验一次资格（前端虽然已预检，恶意请求兜底放在这里）
        ReviewEligibilityVO eligibility = checkEligibility(userId, req.getProductId());
        if (Boolean.FALSE.equals(eligibility.getCanReview())) {
            ResultCode rc = REASON_ALREADY_REVIEWED.equals(eligibility.getReason())
                    ? ResultCode.REVIEW_ALREADY_EXISTS
                    : ResultCode.REVIEW_NOT_PURCHASED;
            throw new BizException(rc);
        }

        TProductReview r = new TProductReview();
        r.setUserId(userId);
        r.setProductId(req.getProductId());
        r.setRating(req.getRating());
        r.setContent(req.getContent().trim());
        r.setIsAnonymous(0);
        r.setIsHidden(0);
        // 兜底设置 createdAt/updatedAt，即便 DB DEFAULT 已生效也能让 Java 端拿到值
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        reviewMapper.insert(r);
        // 同步回写 t_product.rating，保证首页/列表卡与详情页综合评分数据源一致
        try {
            recalcProductRating(req.getProductId());
        } catch (Exception ex) {
            // 回写失败不阻塞评价主流程；记录日志，人工/下次重算兜底
            log.warn("[review] recalc product rating failed productId={} err={}",
                    req.getProductId(), ex.getMessage());
        }
        log.info("[review] user={} product={} rating={} id={}",
                new Object[]{ userId, req.getProductId(), req.getRating(), r.getId() });
        return r.getId();
    }

    /**
     * 重新聚合 t_product_review 的平均分，回写到 t_product.rating。
     * 用 ListReviews 中已有的 fetchRatingStats，避免重复 SQL 模板。
     * 保留 1 位小数（与详情页 avgRatingText 一致）。
     */
    private void recalcProductRating(Long productId) {
        Map<String, Object> stats = fetchRatingStats(productId);
        long total = toLong(stats.get("totalCount"));
        if (total == 0) {
            // 没有可见评价时不强行覆盖（保留商品主表初始值）
            return;
        }
        double avg = stats.get("avgRating") == null ? 0.0 : toDouble(stats.get("avgRating"));
        BigDecimal rounded = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
        int affected = productMapper.updateRating(productId, rounded);
        log.info("[review] recalc product rating productId={} total={} newRating={} affected={}",
                productId, total, rounded, affected);
    }

    // ============================ 私有 ============================

    /**
     * 一次拿回 totalCount / positiveCount / avgRating，避免循环二次查询
     */
    private Map<String, Object> fetchRatingStats(Long productId) {
        List<Map<String, Object>> rows = reviewMapper.selectMaps(
                new QueryWrapper<TProductReview>()
                        .select("COUNT(*) AS totalCount",
                                "SUM(CASE WHEN rating >= 4 THEN 1 ELSE 0 END) AS positiveCount",
                                "AVG(rating) AS avgRating")
                        .eq("product_id", productId)
                        .eq("is_hidden", 0));
        return rows.isEmpty() ? new HashMap<>() : rows.get(0);
    }

    private ReviewListItemVO toVO(TProductReview r, Map<Long, TUser> userMap) {
        ReviewListItemVO vo = new ReviewListItemVO();
        vo.setId(r.getId());
        vo.setRating(r.getRating());
        vo.setContent(r.getContent());
        vo.setAnonymous(r.getIsAnonymous() != null && r.getIsAnonymous() == 1);
        boolean anonymous = Boolean.TRUE.equals(vo.getAnonymous());
        if (anonymous) {
            vo.setDisplayName("匿名用户");
        } else {
            TUser u = userMap.get(r.getUserId());
            vo.setDisplayName(u != null ? (u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername()) : "用户");
            vo.setAvatar(u != null ? u.getAvatar() : null);
        }
        vo.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().format(DT_FMT) : null);
        return vo;
    }

    private static long toLong(Object v) {
        return v == null ? 0L : (v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v)));
    }

    private static double toDouble(Object v) {
        return v == null ? 0.0 : (v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v)));
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
