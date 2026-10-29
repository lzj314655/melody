package com.melody.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.melody.entity.TCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * TCategory 表 Mapper
 *
 * @author lzj
 */
@Mapper
public interface TCategoryMapper extends BaseMapper<TCategory> {

    /**
     * 数据大屏：分类销售占比（F-D-005 零食分类受欢迎程度）
     * 聚合每个启用分类下商品的累计销量（按 t_product.sales 冗余字段），
     * 排除下架商品；按销量倒序返回，qty=0 的分类（即刚加还没出单）不出现在结果里。
     *
     * @return [{categoryId, name, qty}, ...]
     */
    @Select("SELECT p.category_id AS categoryId, "
          + "       c.name        AS name, "
          + "       SUM(p.sales) AS qty "
          + "FROM   t_product p "
          + "JOIN   t_category c ON c.id = p.category_id AND c.status = 1 "
          + "WHERE  p.status = 1 "
          + "GROUP  BY p.category_id, c.name "
          + "HAVING qty > 0 "
          + "ORDER  BY qty DESC, p.category_id ASC "
          + "LIMIT  20")
    List<Map<String, Object>> selectCategoryShare();
}
