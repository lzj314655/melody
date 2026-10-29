package com.melody.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.entity.TProductSku;
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
 * TProductSkuMapper CRUD：service.syncSkus() 依赖 selectList 按 productId 全量拉取。
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@DisplayName("TProductSkuMapper 集成测试")
class TProductSkuMapperTest {

    @Autowired
    private TProductSkuMapper skuMapper;

    @BeforeEach
    void cleanTable() {
        skuMapper.delete(new LambdaQueryWrapper<>());
    }

    private TProductSku seed(Long productId, String specName, BigDecimal price, int stock, int status) {
        TProductSku s = new TProductSku();
        s.setProductId(productId);
        s.setSpecName(specName);
        s.setSkuCode("SKU-" + productId + "-" + specName);
        s.setPrice(price);
        s.setStock(stock);
        s.setImage("/img/x.jpg");
        s.setSales(0);
        s.setStatus(status);
        skuMapper.insert(s);
        return s;
    }

    @Test
    @DisplayName("insert + selectById")
    void insertAndSelect() {
        TProductSku s = seed(1L, "大份", new BigDecimal("19.90"), 100, 1);
        assertThat(s.getId()).isNotNull();

        TProductSku loaded = skuMapper.selectById(s.getId());
        assertThat(loaded.getSpecName()).isEqualTo("大份");
        assertThat(loaded.getStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("按 productId 全量查 — service.syncSkus 依赖此查询")
    void selectByProductId() {
        seed(1L, "小份", new BigDecimal("9.90"), 50, 1);
        seed(1L, "大份", new BigDecimal("19.90"), 30, 1);
        seed(2L, "其他", new BigDecimal("5.00"), 10, 1);

        List<TProductSku> ofP1 = skuMapper.selectList(
                new LambdaQueryWrapper<TProductSku>().eq(TProductSku::getProductId, 1L));
        assertThat(ofP1).hasSize(2);
    }

    @Test
    @DisplayName("按 productId + status 1 过滤 — service.listSkus 行为")
    void selectActiveByProductId() {
        seed(1L, "上架", new BigDecimal("9.90"), 100, 1);
        seed(1L, "下架", new BigDecimal("9.90"), 100, 0);

        List<TProductSku> active = skuMapper.selectList(
                new LambdaQueryWrapper<TProductSku>()
                        .eq(TProductSku::getProductId, 1L)
                        .eq(TProductSku::getStatus, 1));
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getSpecName()).isEqualTo("上架");
    }

    @Test
    @DisplayName("updateById 扣库存 — service.create 扣库存逻辑")
    void updateStock() {
        TProductSku s = seed(1L, "中份", new BigDecimal("12.00"), 10, 1);
        s.setStock(s.getStock() - 3);
        s.setSales(s.getSales() + 3);
        skuMapper.updateById(s);

        TProductSku loaded = skuMapper.selectById(s.getId());
        assertThat(loaded.getStock()).isEqualTo(7);
        assertThat(loaded.getSales()).isEqualTo(3);
    }

    @Test
    @DisplayName("下架 SKU 不计入默认排序结果")
    void softDeleteByStatusZero() {
        TProductSku active = seed(1L, "x", new BigDecimal("1"), 1, 1);
        TProductSku inactive = seed(1L, "y", new BigDecimal("2"), 1, 0);

        TProductSku loaded = skuMapper.selectById(inactive.getId());
        loaded.setStatus(0);
        skuMapper.updateById(loaded);

        List<TProductSku> onSale = skuMapper.selectList(
                new LambdaQueryWrapper<TProductSku>()
                        .eq(TProductSku::getStatus, 1));
        assertThat(onSale).extracting(TProductSku::getId).containsExactly(active.getId());
    }
}
