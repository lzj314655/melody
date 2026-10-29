package com.melody.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.TCategory;
import com.melody.entity.TProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TCategoryMapper 测试：CRUD + selectCategoryShare（JOIN 聚合）。
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("TCategoryMapper 集成测试")
class TCategoryMapperTest {

    @Autowired
    private TCategoryMapper categoryMapper;

    @Autowired
    private TProductMapper productMapper;

    @BeforeEach
    void cleanTable() {
        productMapper.delete(new LambdaQueryWrapper<>());
        categoryMapper.delete(new LambdaQueryWrapper<>());
    }

    private TCategory seedCategory(String name, Integer status) {
        TCategory c = new TCategory();
        c.setName(name);
        c.setSort(0);
        c.setStatus(status);
        categoryMapper.insert(c);
        return c;
    }

    private TProduct seedProduct(Long categoryId, String name, Integer sales, Integer status) {
        TProduct p = new TProduct();
        p.setName(name);
        p.setCategoryId(categoryId);
        p.setPrice(new BigDecimal("9.90"));
        p.setInventory(100);
        p.setSales(sales);
        p.setRating(new BigDecimal("5.00"));
        p.setStatus(status);
        productMapper.insert(p);
        return p;
    }

    @Test
    @DisplayName("CRUD：insert + selectById + updateById + deleteById")
    void crud() {
        TCategory c = seedCategory("零食", 1);

        TCategory loaded = categoryMapper.selectById(c.getId());
        assertThat(loaded.getName()).isEqualTo("零食");
        assertThat(loaded.getStatus()).isEqualTo(1);

        loaded.setName("零嘴");
        int upd = categoryMapper.updateById(loaded);
        assertThat(upd).isEqualTo(1);

        int del = categoryMapper.deleteById(loaded.getId());
        assertThat(del).isEqualTo(1);
        assertThat(categoryMapper.selectById(loaded.getId())).isNull();
    }

    @Test
    @DisplayName("selectList 按 status 过滤 + 排序")
    void selectActiveOrdered() {
        seedCategory("上架A", 1);
        TCategory c2 = seedCategory("上架B", 1);
        c2.setSort(99);
        categoryMapper.updateById(c2);
        seedCategory("下架C", 0);

        List<TCategory> active = categoryMapper.selectList(
                new LambdaQueryWrapper<TCategory>()
                        .eq(TCategory::getStatus, 1)
                        .orderByAsc(TCategory::getSort));
        assertThat(active).hasSize(2);
    }

    @Test
    @DisplayName("selectCategoryShare 聚合 + HAVING qty>0")
    void selectCategoryShare_aggregatesSales() {
        TCategory c1 = seedCategory("薯片", 1);
        TCategory c2 = seedCategory("饮料", 1);
        TCategory c3 = seedCategory("无销量", 1);

        seedProduct(c1.getId(), "乐事薯片", 50, 1);
        seedProduct(c1.getId(), "好丽友", 30, 1);
        seedProduct(c2.getId(), "可乐", 100, 1);
        // c3 没有任何销量 → 被 HAVING qty > 0 过滤
        seedProduct(c3.getId(), "无销量商品", 0, 1);
        // 下架商品不计入
        seedProduct(c2.getId(), "下架商品", 999, 0);

        List<Map<String, Object>> rows = categoryMapper.selectCategoryShare();
        assertThat(rows).hasSize(2);

        Map<String, Object> first = rows.get(0);
        assertThat(first.get("name")).isEqualTo("饮料");
        assertThat(((Number) first.get("qty")).intValue()).isEqualTo(100);

        Map<String, Object> second = rows.get(1);
        assertThat(second.get("name")).isEqualTo("薯片");
        assertThat(((Number) second.get("qty")).intValue()).isEqualTo(80);
    }

    @Test
    @DisplayName("selectCategoryShare 表为空时返回空列表")
    void selectCategoryShare_empty() {
        List<Map<String, Object>> rows = categoryMapper.selectCategoryShare();
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("selectCategoryShare 下架分类不计入")
    void selectCategoryShare_offShelfCategoryExcluded() {
        TCategory off = seedCategory("下架分类", 0);
        seedProduct(off.getId(), "x", 50, 1);

        List<Map<String, Object>> rows = categoryMapper.selectCategoryShare();
        assertThat(rows).isEmpty();
    }
}
