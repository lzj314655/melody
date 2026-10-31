package com.melody.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.melody.entity.TProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * TProductSku 表 Mapper
 *
 * @author lzj
 */
@Mapper
public interface TProductSkuMapper extends BaseMapper<TProductSku> {

    /**
     * 原子扣减库存。其中 {@code stock >= qty} 是并发安全的关键：
     * 两个订单同时扣同一 SKU 时，数据库行锁保证只有库存足够的那次能成功。
     *
     * @return 1=扣减成功，0=库存不足或 SKU 不存在
     */
    @Update("UPDATE t_product_sku " +
            "SET stock = stock - #{qty}, sales = COALESCE(sales, 0) + #{qty} " +
            "WHERE id = #{id} AND stock >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /** 取消/退款时原子回滚库存，销量不低于 0。 */
    @Update("UPDATE t_product_sku " +
            "SET stock = stock + #{qty}, sales = GREATEST(COALESCE(sales, 0) - #{qty}, 0) " +
            "WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);
}
