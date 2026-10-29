package com.melody.service;

import com.melody.dto.CheckoutDTOs.CartItemVO;
import com.melody.entity.TCartItem;

import java.util.List;

/**
 * 购物车 Service（F-U-030 ~ F-U-036 + F-U-034 多选）
 */
public interface ICartService {

    List<CartItemVO> list(Long userId);

    TCartItem add(Long userId, Long productId, Long skuId, Integer quantity, java.math.BigDecimal price, String specName);

    void updateQuantity(Long userId, Long productId, Long skuId, Integer quantity);

    void remove(Long userId, Long productId, Long skuId);

    /** F-U-034 切换单条选中 */
    void toggleSelect(Long userId, Long productId, Long skuId);

    /** F-U-034 显式设置单条选中状态 */
    void setSelect(Long userId, Long productId, Long skuId, boolean selected);

    /** F-U-034 全选/取消全选 */
    void toggleSelectAll(Long userId, boolean selectAll);

    /** F-U-034 结算后清理已选 */
    void clearSelected(Long userId);
}