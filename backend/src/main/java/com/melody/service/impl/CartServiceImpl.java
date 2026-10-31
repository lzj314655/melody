package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.dto.CheckoutDTOs.CartItemVO;
import com.melody.entity.TCartItem;
import com.melody.entity.TProduct;
import com.melody.entity.TProductSku;
import com.melody.exception.BizException;
import com.melody.common.ResultCode;
import com.melody.mapper.TCartItemMapper;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.TProductSkuMapper;
import com.melody.service.ICartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements ICartService {

    private final TCartItemMapper cartMapper;
    private final TProductSkuMapper skuMapper;
    private final TProductMapper productMapper;

    @Override
    public List<CartItemVO> list(Long userId) {
        List<TCartItem> items = cartMapper.selectList(new LambdaQueryWrapper<TCartItem>()
                .eq(TCartItem::getUserId, userId)
                .orderByDesc(TCartItem::getCreatedAt));
        if (items.isEmpty()) return Collections.emptyList();

        // 批量回填商品名/封面图，避免前端只显示规格名
        List<Long> productIds = items.stream()
                .map(TCartItem::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, TProduct> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(TProduct::getId, p -> p));

        return items.stream().map(i -> {
            CartItemVO vo = new CartItemVO();
            vo.setId(i.getId());
            vo.setProductId(i.getProductId());
            vo.setSkuId(i.getSkuId());
            vo.setQuantity(i.getQuantity());
            vo.setPrice(i.getPrice());
            vo.setSpecName(i.getSpecName());
            vo.setSelected(i.getSelected());
            TProduct p = productMap.get(i.getProductId());
            if (p != null) {
                vo.setProductName(p.getName());
                vo.setProductImage(p.getCoverImage());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TCartItem add(Long userId, Long productId, Long skuId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "数量必须大于 0");
        }
        // 价格与规格名一律以数据库为准，忽略任何客户端传值
        TProduct product = productMapper.selectById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF, "商品不存在或已下架");
        }
        TProductSku sku = skuMapper.selectById(skuId);
        if (sku == null || sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "规格不存在或已下架");
        }
        if (!productId.equals(sku.getProductId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "规格与商品不匹配");
        }
        BigDecimal price = sku.getPrice();
        String specName = sku.getSpecName();
        if (price == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "商品价格异常，请稍后再试");
        }
        TCartItem exist = cartMapper.selectOne(new LambdaQueryWrapper<TCartItem>()
                .eq(TCartItem::getUserId, userId)
                .eq(TCartItem::getProductId, productId)
                .eq(TCartItem::getSkuId, skuId));
        if (exist != null) {
            exist.setQuantity(exist.getQuantity() + quantity);
            cartMapper.updateById(exist);
            return exist;
        }
        TCartItem item = new TCartItem();
        item.setUserId(userId);
        item.setProductId(productId);
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        item.setPrice(price);
        item.setSpecName(specName);
        item.setSelected(1);
        cartMapper.insert(item);
        return item;
    }

    @Override
    @Transactional
    public void updateQuantity(Long userId, Long productId, Long skuId, Integer quantity) {
        TCartItem item = cartMapper.selectOne(new LambdaQueryWrapper<TCartItem>()
                .eq(TCartItem::getUserId, userId)
                .eq(TCartItem::getProductId, productId)
                .eq(TCartItem::getSkuId, skuId));
        if (item == null) throw new BizException(ResultCode.NOT_FOUND, "购物车项不存在");
        if (quantity == null || quantity <= 0) {
            cartMapper.deleteById(item.getId());
        } else {
            item.setQuantity(quantity);
            cartMapper.updateById(item);
        }
    }

    @Override
    @Transactional
    public void remove(Long userId, Long productId, Long skuId) {
        cartMapper.delete(new LambdaQueryWrapper<TCartItem>()
                .eq(TCartItem::getUserId, userId)
                .eq(TCartItem::getProductId, productId)
                .eq(TCartItem::getSkuId, skuId));
    }

    @Override
    @Transactional
    public void toggleSelect(Long userId, Long productId, Long skuId) {
        TCartItem item = cartMapper.selectOne(new LambdaQueryWrapper<TCartItem>()
                .eq(TCartItem::getUserId, userId)
                .eq(TCartItem::getProductId, productId)
                .eq(TCartItem::getSkuId, skuId));
        if (item == null) return;
        item.setSelected(item.getSelected() == null || item.getSelected() == 0 ? 1 : 0);
        cartMapper.updateById(item);
    }

    @Override
    @Transactional
    public void setSelect(Long userId, Long productId, Long skuId, boolean selected) {
        TCartItem item = cartMapper.selectOne(new LambdaQueryWrapper<TCartItem>()
                .eq(TCartItem::getUserId, userId)
                .eq(TCartItem::getProductId, productId)
                .eq(TCartItem::getSkuId, skuId));
        if (item == null) return;
        item.setSelected(selected ? 1 : 0);
        cartMapper.updateById(item);
    }

    @Override
    @Transactional
    public void toggleSelectAll(Long userId, boolean selectAll) {
        List<TCartItem> items = cartMapper.selectList(new LambdaQueryWrapper<TCartItem>()
                .eq(TCartItem::getUserId, userId));
        for (TCartItem i : items) {
            i.setSelected(selectAll ? 1 : 0);
            cartMapper.updateById(i);
        }
    }

    @Override
    @Transactional
    public void clearSelected(Long userId) {
        List<TCartItem> items = cartMapper.selectList(new LambdaQueryWrapper<TCartItem>()
                .eq(TCartItem::getUserId, userId));
        for (TCartItem i : items) {
            if (i.getSelected() != null && i.getSelected() == 1) {
                cartMapper.deleteById(i.getId());
            }
        }
    }
}