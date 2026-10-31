package com.melody.controller;

import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.dto.CheckoutDTOs;
import com.melody.dto.CheckoutDTOs.CartItemVO;
import com.melody.entity.TCartItem;
import com.melody.service.ICartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车接口（PRD §8.1 /api/cart，F-U-030 ~ F-U-036）
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final ICartService cartService;

    @GetMapping
    public R<List<CartItemVO>> list() {
        return R.ok(cartService.list(SecurityUtils.currentId()));
    }

    @PostMapping
    public R<TCartItem> add(@Valid @RequestBody CheckoutDTOs.CartAddRequest req) {
        return R.ok(cartService.add(SecurityUtils.currentId(),
                req.getProductId(), req.getSkuId(), req.getQuantity()));
    }

    @PutMapping("/{productId}/{skuId}")
    public R<Void> updateQty(@PathVariable Long productId, @PathVariable Long skuId,
                             @RequestParam Integer quantity) {
        cartService.updateQuantity(SecurityUtils.currentId(), productId, skuId, quantity);
        return R.ok();
    }

    @DeleteMapping("/{productId}/{skuId}")
    public R<Void> remove(@PathVariable Long productId, @PathVariable Long skuId) {
        cartService.remove(SecurityUtils.currentId(), productId, skuId);
        return R.ok();
    }

    /** F-U-034 切换单条选中 */
    @PutMapping("/{productId}/{skuId}/select")
    public R<Void> toggleSelect(@PathVariable Long productId, @PathVariable Long skuId,
                                @RequestBody(required = false) java.util.Map<String, Object> body) {
        boolean selected = body != null && Boolean.TRUE.equals(body.get("selected"));
        cartService.setSelect(SecurityUtils.currentId(), productId, skuId, selected);
        return R.ok();
    }

    /** F-U-034 全选/取消全选 */
    @PutMapping("/select-all")
    public R<Void> toggleSelectAll(@RequestParam(required = false) Boolean selectAll,
                                   @RequestBody(required = false) java.util.Map<String, Object> body) {
        Boolean s = selectAll != null ? selectAll
                : (body != null && Boolean.TRUE.equals(body.get("selected")) ? Boolean.TRUE : Boolean.FALSE);
        cartService.toggleSelectAll(SecurityUtils.currentId(), s);
        return R.ok();
    }
}