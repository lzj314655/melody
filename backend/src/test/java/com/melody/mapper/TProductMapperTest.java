package com.melody.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TProductMapper 测试：CRUD + updateRating 自定义 SQL。
 *
 * <p>tags JSON 列在 H2 schema-test.sql 中已改为 TEXT，
 * JacksonTypeHandler 仍由 MyBatis-Plus 处理（写入字符串，读取为 List）。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("TProductMapper 集成测试")
class TProductMapperTest {

    @Autowired
    private TProductMapper productMapper;

    @BeforeEach
    void cleanTable() {
        productMapper.delete(new LambdaQueryWrapper<>());
    }

    private TProduct newProduct(String name, BigDecimal price, Integer status) {
        TProduct p = new TProduct();
        p.setName(name);
        p.setCategoryId(1L);
        p.setBrand("测试品牌");
        p.setPrice(price);
        p.setMarketPrice(price.add(new BigDecimal("10")));
        p.setDescription(name + " 的描述");
        p.setCoverImage("/img/" + name + ".jpg");
        p.setInventory(100);
        p.setSales(0);
        p.setRating(new BigDecimal("5.00"));
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("insert + selectById 保持字段一致")
    void insertAndSelect() {
        TProduct p = newProduct("可乐", new BigDecimal("3.50"), 1);
        productMapper.insert(p);
        assertThat(p.getId()).isNotNull();

        TProduct loaded = productMapper.selectById(p.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("可乐");
        assertThat(loaded.getPrice()).isEqualByComparingTo("3.50");
        assertThat(loaded.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("按 status 过滤 + 排序")
    void selectByStatusAndOrder() {
        productMapper.insert(newProduct("p1", new BigDecimal("10"), 1));
        productMapper.insert(newProduct("p2", new BigDecimal("20"), 1));
        productMapper.insert(newProduct("p3", new BigDecimal("30"), 0));

        List<TProduct> onShelf = productMapper.selectList(
                new LambdaQueryWrapper<TProduct>()
                        .eq(TProduct::getStatus, 1)
                        .orderByAsc(TProduct::getPrice));
        assertThat(onShelf).hasSize(2);
        assertThat(onShelf.get(0).getPrice()).isEqualByComparingTo("10");
        assertThat(onShelf.get(1).getPrice()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("分页：第 1 页 2 条 / 第 2 页 1 条")
    void pagination() {
        for (int i = 0; i < 5; i++) {
            productMapper.insert(newProduct("p" + i, new BigDecimal(i + 1), 1));
        }

        Page<TProduct> page1 = productMapper.selectPage(
                new Page<>(1, 2),
                new LambdaQueryWrapper<TProduct>().orderByAsc(TProduct::getId));
        assertThat(page1.getRecords()).hasSize(2);
        assertThat(page1.getTotal()).isEqualTo(5);

        Page<TProduct> page2 = productMapper.selectPage(
                new Page<>(2, 2),
                new LambdaQueryWrapper<TProduct>().orderByAsc(TProduct::getId));
        assertThat(page2.getRecords()).hasSize(2);
    }

    @Test
    @DisplayName("updateRating 回写评分")
    void updateRating() {
        TProduct p = newProduct("打分商品", new BigDecimal("9.9"), 1);
        productMapper.insert(p);

        int affected = productMapper.updateRating(p.getId(), new BigDecimal("4.7"));
        assertThat(affected).isEqualTo(1);

        TProduct loaded = productMapper.selectById(p.getId());
        assertThat(loaded.getRating()).isEqualByComparingTo("4.7");
    }

    @Test
    @DisplayName("updateRating 对不存在的 id 返回 0")
    void updateRatingMissing() {
        int affected = productMapper.updateRating(99999L, new BigDecimal("5.0"));
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("selectList 不带条件返回全部")
    void selectAll() {
        productMapper.insert(newProduct("a", new BigDecimal("1"), 1));
        productMapper.insert(newProduct("b", new BigDecimal("2"), 1));
        List<TProduct> all = productMapper.selectList(new LambdaQueryWrapper<>());
        assertThat(all).hasSize(2);
    }
}
