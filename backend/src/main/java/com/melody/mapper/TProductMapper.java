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
}
