package com.melody.service.recommend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.UserBehaviorLog;
import com.melody.entity.enums.BehaviorTypeEnum;

import java.util.function.Consumer;

/**
 * 行为查询的统一过滤策略（推荐系统数据卫生）
 *
 * 问题：列表页 watch(filteredProducts) 曾对列表里每个商品批量上报 source=list 的 VIEW，
 * 把"曝光"误标成"浏览"，库里堆积大量 list 垃圾 VIEW。若 ItemCF 把这些曝光当真实兴趣，
 * 每个用户都会"看过"一堆列表商品，拉低相似度区分度，污染推荐结果与 Redis 缓存。
 *
 * 策略：非 VIEW 行为（ADD_CART/FAVORITE/ORDER，强兴趣信号）全部纳入；
 *       VIEW 行为只取 source=detail（真正点开详情页）及早期未带 source 的 NULL，
 *       排除 source=list/test/uat 的曝光垃圾。
 */
public final class BehaviorScopes {

    private BehaviorScopes() {}

    /**
     * 用于 LambdaQueryWrapper.and(...)：返回"真实行为"过滤条件。
     * 生成 SQL：AND (behavior_type != 'VIEW' OR (source='detail' OR source IS NULL))
     */
    public static Consumer<LambdaQueryWrapper<UserBehaviorLog>> realBehaviorOnly() {
        return w -> w
                .ne(UserBehaviorLog::getBehaviorType, BehaviorTypeEnum.VIEW.getCode())
                .or()
                .apply("(JSON_UNQUOTE(JSON_EXTRACT(extra, '$.source')) = {0} "
                        + "OR JSON_UNQUOTE(JSON_EXTRACT(extra, '$.source')) IS NULL)", "detail");
    }
}
