package com.melody.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.melody.entity.TProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * TProduct 表 Mapper
 *
 * @author lzj
 */
@Mapper
public interface TProductMapper extends BaseMapper<TProduct> {

    /**
     * 回写商品平均评分（F-U-062 评价提交后同步刷新 t_product.rating，
     * 保证首页/列表卡与详情页综合评分数据源一致）
     *
     * @param id     商品 ID
     * @param rating 重新聚合后的平均分（0-5，保留 1 位小数）
     * @return 影响行数
     */
    @Update("UPDATE t_product SET rating = #{rating} WHERE id = #{id}")
    int updateRating(@Param("id") Long id, @Param("rating") BigDecimal rating);

    /** 原子累加商品销量（并发下单不会互相覆盖）。 */
    @Update("UPDATE t_product SET sales = COALESCE(sales, 0) + #{qty} WHERE id = #{id}")
    int addSales(@Param("id") Long id, @Param("qty") int qty);

    /** 取消/退款时原子回滚销量，不低于 0。 */
    @Update("UPDATE t_product SET sales = GREATEST(COALESCE(sales, 0) - #{qty}, 0) WHERE id = #{id}")
    int subtractSales(@Param("id") Long id, @Param("qty") int qty);

    /** 无 SKU 商品：原子扣减商品库存。 */
    @Update("UPDATE t_product SET inventory = inventory - #{qty} " +
            "WHERE id = #{id} AND inventory >= #{qty}")
    int deductInventory(@Param("id") Long id, @Param("qty") int qty);

    /** 无 SKU 商品：取消/退款时回滚商品库存。 */
    @Update("UPDATE t_product SET inventory = inventory + #{qty} WHERE id = #{id}")
    int restoreInventory(@Param("id") Long id, @Param("qty") int qty);

    /**
     * 以在架 SKU 库存之和为准重算商品库存，避免商品表冗余字段在多 SKU 并发扣减后漂移。
     * 注意：子查询只读 t_product_sku，不违反 MySQL「不能更新正在查询的同一张表」限制。
     */
    @Update("UPDATE t_product SET inventory = (" +
            "  SELECT COALESCE(SUM(s.stock), 0) FROM t_product_sku s " +
            "  WHERE s.product_id = t_product.id AND s.status = 1" +
            ") WHERE id = #{id}")
    int syncInventoryFromSkus(@Param("id") Long id);
}
