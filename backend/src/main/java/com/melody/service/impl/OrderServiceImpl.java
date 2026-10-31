package com.melody.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.melody.exception.BizException;
import com.melody.common.ResultCode;
import com.melody.entity.TCartItem;
import com.melody.entity.TCoupon;
import com.melody.entity.TOrder;
import com.melody.entity.TOrderItem;
import com.melody.entity.TPointLog;
import com.melody.entity.TProduct;
import com.melody.entity.TProductSku;
import com.melody.entity.TUserAddress;
import com.melody.entity.TUserCoupon;
import com.melody.entity.TUser;
import com.melody.entity.TGrowthLog;
import com.melody.mapper.TCartItemMapper;
import com.melody.mapper.TCouponMapper;
import com.melody.mapper.TGrowthLogMapper;
import com.melody.mapper.TOrderItemMapper;
import com.melody.mapper.TOrderMapper;
import com.melody.mapper.TPointLogMapper;
import com.melody.mapper.TProductMapper;
import com.melody.mapper.TProductSkuMapper;
import com.melody.mapper.TUserCouponMapper;
import com.melody.mapper.TUserMapper;
import com.melody.service.ICouponService;
import com.melody.service.IOrderService;
import com.melody.service.IWalletService;
import com.melody.service.OrderNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements IOrderService {

    private final TOrderMapper orderMapper;
    private final TOrderItemMapper orderItemMapper;
    private final TCartItemMapper cartMapper;
    private final TProductMapper productMapper;
    private final TProductSkuMapper skuMapper;
    private final TCouponMapper couponMapper;
    private final TUserCouponMapper userCouponMapper;
    private final TPointLogMapper pointLogMapper;
    private final TGrowthLogMapper growthLogMapper;
    private final IWalletService walletService;
    private final ICouponService couponService;
    private final TUserMapper userMapper;
    private final OrderNotifyService orderNotifyService;

    /** PRD §7.5 会员等级成长值阈值 */
    private static final int RANK_V1 = 500;
    private static final int RANK_V2 = 2000;
    private static final int RANK_V3 = 5000;

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ====================== F-U-040 创建订单 ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TOrder create(Long userId, List<Long> cartItemIds, TUserAddress address, Long userCouponId, String payMethod) {
        boolean isAlipay = "ALIPAY".equalsIgnoreCase(payMethod);
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new BizException(ResultCode.CART_EMPTY);
        }
        // F-A-042: 黑名单用户禁止下单（管理员冻结后即时生效）
        TUser u = userMapper.selectById(userId);
        if (u == null) throw new BizException(ResultCode.USER_NOT_FOUND);
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被冻结，无法下单");
        }
        // 取购物车项（只保留本人的，且必须至少剩一条）
        List<TCartItem> items = cartMapper.selectBatchIds(cartItemIds);
        items.removeIf(i -> !i.getUserId().equals(userId));
        if (items.isEmpty()) throw new BizException(ResultCode.CART_EMPTY);

        // 算金额 + 校验库存：单价一律以数据库中的 SKU / 商品现价为准，
        // 不信任购物车里的 price 快照（防止前端篡改或历史脏数据）。
        BigDecimal goodsAmount = BigDecimal.ZERO;
        Map<Long, TProductSku> skuByItem = new HashMap<>();
        Map<Long, BigDecimal> unitPriceByItem = new HashMap<>();
        for (TCartItem ci : items) {
            if (ci.getQuantity() == null || ci.getQuantity() <= 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "购买数量不合法");
            }
            TProduct p = productMapper.selectById(ci.getProductId());
            if (p == null || p.getStatus() == null || p.getStatus() != 1) {
                throw new BizException(ResultCode.PRODUCT_OFF_SHELF, "商品已下架");
            }
            BigDecimal unitPrice;
            if (ci.getSkuId() != null) {
                TProductSku sku = skuMapper.selectById(ci.getSkuId());
                if (sku == null || sku.getStatus() == null || sku.getStatus() != 1
                        || !ci.getProductId().equals(sku.getProductId())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "商品规格不存在或与商品不匹配");
                }
                if (sku.getStock() == null || sku.getStock() < ci.getQuantity()) {
                    throw new BizException(ResultCode.PRODUCT_OUT_OF_STOCK);
                }
                unitPrice = sku.getPrice();
                skuByItem.put(ci.getId(), sku);
            } else {
                if (p.getInventory() == null || p.getInventory() < ci.getQuantity()) {
                    throw new BizException(ResultCode.PRODUCT_OUT_OF_STOCK);
                }
                unitPrice = p.getPrice();
            }
            if (unitPrice == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "商品价格异常");
            }
            unitPriceByItem.put(ci.getId(), unitPrice);
            goodsAmount = goodsAmount.add(unitPrice.multiply(BigDecimal.valueOf(ci.getQuantity())));
        }

        // 优惠券
        BigDecimal discount = BigDecimal.ZERO;
        TUserCoupon uc = null;
        if (userCouponId != null) {
            uc = userCouponMapper.selectById(userCouponId);
            if (uc == null || !"可用".equals(uc.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST, "优惠券不可用");
            }
            if (!uc.getUserId().equals(userId)) {
                throw new BizException(ResultCode.FORBIDDEN, "无权使用他人优惠券");
            }
            TCoupon c = couponMapper.selectById(uc.getCouponId());
            if (c == null || goodsAmount.compareTo(c.getMinSpend()) < 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "未达优惠券使用门槛");
            }
            if ("折扣".equals(c.getType())) {
                // 折扣券：discount 字段为折扣率（如 0.8 = 八折）
                // 优惠金额 = 商品总额 × (1 - 折扣率)，封顶 50 元
                BigDecimal raw = goodsAmount.multiply(BigDecimal.ONE.subtract(c.getDiscount()));
                BigDecimal cap = new BigDecimal("50");
                discount = raw.min(cap);
            } else {
                // 满减券：discount 字段为减金额
                discount = c.getDiscount();
            }
        }

        BigDecimal totalPrice = goodsAmount.subtract(discount);
        // 兜底：负数按 0 处理；演示场景避免 ¥0.00 单，最低收 ¥0.01
        if (totalPrice.signum() < 0) totalPrice = BigDecimal.ZERO;
        BigDecimal MIN_PRICE = new BigDecimal("0.01");
        if (totalPrice.compareTo(MIN_PRICE) < 0 && goodsAmount.compareTo(MIN_PRICE) >= 0) {
            totalPrice = MIN_PRICE;
        }

        // 扣库存：必须走带 stock >= qty 条件的原子 SQL，避免并发下单超卖。
        for (TCartItem ci : items) {
            int qty = ci.getQuantity();
            TProductSku sku = skuByItem.get(ci.getId());
            if (sku != null) {
                if (skuMapper.deductStock(sku.getId(), qty) != 1) {
                    throw new BizException(ResultCode.PRODUCT_OUT_OF_STOCK);
                }
                productMapper.addSales(ci.getProductId(), qty);
                // 商品表 inventory 是 SKU 库存之和的冗余字段，扣完按 SKU 重算
                productMapper.syncInventoryFromSkus(ci.getProductId());
            } else {
                if (productMapper.deductInventory(ci.getProductId(), qty) != 1) {
                    throw new BizException(ResultCode.PRODUCT_OUT_OF_STOCK);
                }
                productMapper.addSales(ci.getProductId(), qty);
            }
        }

        // 建订单：先 set 所有字段，再 insert + 重试避免 uk_order_no 冲突
        TOrder order = new TOrder();
        order.setUserId(userId);
        order.setTotalPrice(totalPrice);
        order.setGoodsAmount(goodsAmount);
        order.setDiscount(discount);
        order.setShippingFee(BigDecimal.ZERO);
        order.setCouponId(uc == null ? null : uc.getCouponId());
        order.setStatus(isAlipay ? "PENDING_PAY" : "PENDING_SHIP"); // ALIPAY 待付款；WALLET 即时支付
        order.setPayMethod(isAlipay ? "ALIPAY" : "WALLET");
        order.setConsigneeName(address.getName());
        order.setConsigneePhone(address.getPhone());
        order.setConsigneeAddress(address.getProvince() + " " + address.getCity() + " " + address.getDistrict() + " " + address.getDetail());
        if (!isAlipay) order.setPaidAt(LocalDateTime.now());

        int maxRetry = 5;
        Exception lastDup = null;
        for (int i = 0; i < maxRetry; i++) {
            order.setOrderNo(genOrderNo());
            try {
                orderMapper.insert(order);
                lastDup = null;
                break;
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("Duplicate") || msg.contains("uk_order_no")) {
                    log.warn("[Order] order_no 冲突，重试第{}次: {}", i + 1, order.getOrderNo());
                    lastDup = ex;
                    continue;
                }
                throw ex;
            }
        }
        if (lastDup != null) throw new BizException(ResultCode.BAD_REQUEST, "下单失败：订单号冲突，请重试");

        // WALLET：即时扣款（orderId 现已拿到，传真实值）。ALIPAY 不扣，保持 PENDING_PAY
        if (!isAlipay) {
            walletService.pay(userId, totalPrice, order.getId());
        }

        // 写明细（价格使用服务端实时价）
        for (TCartItem ci : items) {
            TProduct p = productMapper.selectById(ci.getProductId());
            BigDecimal unitPrice = unitPriceByItem.get(ci.getId());
            TOrderItem oi = new TOrderItem();
            oi.setOrderId(order.getId());
            oi.setProductId(ci.getProductId());
            oi.setSkuId(ci.getSkuId());
            oi.setProductName(p.getName());
            oi.setProductImage(p.getCoverImage());
            oi.setSpecName(ci.getSpecName());
            oi.setPrice(unitPrice);
            oi.setQuantity(ci.getQuantity());
            oi.setSubtotal(unitPrice.multiply(BigDecimal.valueOf(ci.getQuantity())));
            orderItemMapper.insert(oi);
        }

        // 清掉已结算的购物车项
        for (TCartItem ci : items) cartMapper.deleteById(ci.getId());

        // 核销优惠券
        if (uc != null) couponService.consume(userId, uc.getId(), order.getId());

        // PRD §7.3: 实付金额 × 1 积分（ALIPAY 待付款不赠，付款确认 confirmAlipayPaid 里加）
        if (!isAlipay) addPointsAndGrowth(userId, order.getId(), totalPrice);

        // 事务提交后广播新订单事件（后台实时通知；回滚则不广播）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                orderNotifyService.broadcastNewOrder(order);
            }
        });

        log.info("[Order] created orderNo={} userId={} total={} payMethod={}", order.getOrderNo(), userId, totalPrice, isAlipay ? "ALIPAY" : "WALLET");
        return order;
    }

    /** 余额补付 PENDING_PAY 订单：原子流转 + 扣款 + 积分，@Transactional 保证扣款失败回滚 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TOrder payByWallet(Long userId, Long orderId) {
        TOrder o = orderMapper.selectById(orderId);
        if (o == null) throw new BizException(ResultCode.ORDER_NOT_FOUND);
        if (!o.getUserId().equals(userId)) throw new BizException(ResultCode.FORBIDDEN, "无权操作该订单");
        if (!"PENDING_PAY".equals(o.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "订单不在待付款状态");
        }
        // 先原子流转（affected=1 才有效），再扣款；扣款失败事务回滚，状态回到 PENDING_PAY
        int affected = orderMapper.markPaidByWallet(orderId);
        if (affected == 1) {
            walletService.pay(userId, o.getTotalPrice(), orderId);
            addPointsAndGrowth(userId, orderId, o.getTotalPrice());
        }
        return orderMapper.selectById(orderId);
    }

    /** 支付宝付款确认：原子流转 PENDING_PAY→PENDING_SHIP + 加积分（幂等，主动查询/异步通知共用） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmAlipayPaid(String orderNo, String tradeNo) {
        int affected = orderMapper.markPaidByAlipay(orderNo, tradeNo == null ? "" : tradeNo);
        if (affected == 0) return; // 已被并发处理过或非待付款，幂等跳过
        TOrder o = orderMapper.selectOne(new LambdaQueryWrapper<TOrder>()
                .eq(TOrder::getOrderNo, orderNo));
        if (o != null) {
            addPointsAndGrowth(o.getUserId(), o.getId(), o.getTotalPrice());
        }
        log.info("[Order] alipay paid confirmed orderNo={} tradeNo={}", orderNo, tradeNo);
    }

    private void addPointsAndGrowth(Long userId, Long orderId, BigDecimal totalPrice) {
        int delta = totalPrice.intValue(); // 1 元 = 1 积分 = 1 成长值
        TUser u = userMapper.selectById(userId);
        if (u == null) return;

        int newPoints = (u.getPoints() == null ? 0 : u.getPoints()) + delta;
        int newGrowth = (u.getGrowthValue() == null ? 0 : u.getGrowthValue()) + delta;

        // 1) 积分流水：balance_after 必须写累计余额，而不是本次增量
        TPointLog pl = new TPointLog();
        pl.setUserId(userId);
        pl.setChangeAmount(delta);
        pl.setBalanceAfter(newPoints);
        pl.setType("ORDER");
        pl.setReason("订单完成赠送积分");
        pl.setRelatedOrderId(orderId);
        pointLogMapper.insert(pl);

        // 2) 成长值流水
        TGrowthLog gl = new TGrowthLog();
        gl.setUserId(userId);
        gl.setChangeAmount(delta);
        gl.setBalanceAfter(newGrowth);
        gl.setType("ORDER");
        gl.setReason("订单完成累计成长值");
        gl.setRelatedOrderId(orderId);
        growthLogMapper.insert(gl);

        // 3) 同步累加 t_user 字段，并按 PRD §7.5 阈值自动升档
        String newRank = computeRank(newGrowth);
        u.setPoints(newPoints);
        u.setGrowthValue(newGrowth);
        if (newRank != null && !newRank.equals(u.getRankCode())) {
            log.info("[Rank] userId={} {} -> {} (growth={})", userId, u.getRankCode(), newRank, newGrowth);
            u.setRankCode(newRank);
        }
        userMapper.updateById(u);
    }

    /** PRD §7.5: 0-499 V0 / 500-1999 V1 / 2000-4999 V2 / 5000+ V3 */
    private String computeRank(int growth) {
        if (growth >= RANK_V3) return "V3";
        if (growth >= RANK_V2) return "V2";
        if (growth >= RANK_V1) return "V1";
        return "V0";
    }

    private String genOrderNo() {
        // ORD-yyyyMMdd-HHmmssSSS-xxxx（21 字符），碰撞概率极低
        String dt = LocalDateTime.now().format(ORDER_NO_FMT) + "-"
                + String.format("%09d", java.time.LocalDateTime.now().getNano() / 1_000); // HHmmssSSS
        int rand = ThreadLocalRandom.current().nextInt(10000); // 4 位
        return "ORD-" + dt + "-" + String.format("%04d", rand);
    }

    // ====================== F-U-043 取消订单 ======================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long orderId) {
        TOrder o = mustOrder(orderId);
        if (!o.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作他人订单");
        }
        if (!"PENDING_PAY".equals(o.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅待付款订单可取消");
        }
        // 原子流转，避免两个并发取消请求都把库存回滚一遍
        int affected = orderMapper.markCanceled(orderId);
        if (affected != 1) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
        o.setStatus("CANCELED");
        // 未付款订单取消：回滚库存 + 退回优惠券
        rollbackStock(o);
        if (o.getCouponId() != null) {
            couponService.releaseByOrder(userId, orderId);
        }
        log.info("[Order] canceled orderId={}", orderId);
    }

    // ====================== F-U-042 确认收货 ======================
    @Override
    @Transactional
    public void confirmDelivery(Long userId, Long orderId) {
        TOrder o = mustOrder(orderId);
        if (!o.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (!"PENDING_RECEIVE".equals(o.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅待收货订单可确认收货");
        }
        o.setStatus("COMPLETED");
        o.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(o);
    }

    // ====================== F-U-044 申请退款 ======================
    @Override
    @Transactional
    public void refund(Long userId, Long orderId, String reason) {
        TOrder o = mustOrder(orderId);
        if (!o.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (!"PENDING_RECEIVE".equals(o.getStatus()) && !"COMPLETED".equals(o.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅待收货/已完成订单可申请退款");
        }
        o.setStatus("AFTER_SALE");
        o.setRefundReason(reason);
        o.setRefundTime(LocalDateTime.now());
        orderMapper.updateById(o);
    }

    @Override
    @Transactional
    public void ship(Long orderId, String trackingNo) {
        TOrder o = mustOrder(orderId);
        if (!"PENDING_SHIP".equals(o.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅待发货订单可发货");
        }
        o.setStatus("PENDING_RECEIVE");
        o.setTrackingNo(trackingNo);
        o.setShippedAt(LocalDateTime.now());
        orderMapper.updateById(o);
    }

    /** 后台退款审核通过：退钱 + 状态 → REFUNDED + PENDING_SHIP 退款回滚库存、其他不回滚（PRD §7.2） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveRefund(Long orderId) {
        TOrder o = mustOrder(orderId);
        if (!"AFTER_SALE".equals(o.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅售后中订单可审核");
        }
        // 原子流转：两个管理员同时点“通过”时只有一笔能进入退款
        int affected = orderMapper.markRefunded(orderId);
        if (affected != 1) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
        walletService.refund(o.getUserId(), o.getTotalPrice(), orderId);
        o.setStatus("REFUNDED");
        // PRD §7.2: COMPLETED 状态退款不回滚库存（已发货），这里演示同样不回滚
    }

    @Override
    public TOrder getById(Long orderId) {
        return mustOrder(orderId);
    }

    @Override
    public TOrder getByIdForUser(Long orderId, Long userId) {
        TOrder o = mustOrder(orderId);
        if (!o.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看该订单");
        }
        return o;
    }

    @Override
    public TOrder getByOrderNoForUser(String orderNo, Long userId) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单号不能为空");
        }
        TOrder o = orderMapper.selectOne(new LambdaQueryWrapper<TOrder>()
                .eq(TOrder::getOrderNo, orderNo));
        if (o == null) throw new BizException(ResultCode.ORDER_NOT_FOUND);
        if (!o.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看该订单");
        }
        return o;
    }

    @Override
    public List<TOrder> listByUser(Long userId, String status) {
        LambdaQueryWrapper<TOrder> qw = new LambdaQueryWrapper<TOrder>()
                .eq(TOrder::getUserId, userId)
                .orderByDesc(TOrder::getId);
        if (status != null && !"ALL".equalsIgnoreCase(status)) {
            qw.eq(TOrder::getStatus, status);
        }
        return orderMapper.selectList(qw);
    }

    @Override
    public List<TOrder> listAll(String status) {
        LambdaQueryWrapper<TOrder> qw = new LambdaQueryWrapper<TOrder>()
                .orderByDesc(TOrder::getId);
        if (status != null && !"ALL".equalsIgnoreCase(status)) {
            qw.eq(TOrder::getStatus, status);
        }
        return orderMapper.selectList(qw);
    }

    private TOrder mustOrder(Long orderId) {
        TOrder o = orderMapper.selectById(orderId);
        if (o == null) throw new BizException(ResultCode.ORDER_NOT_FOUND);
        return o;
    }

    private void rollbackStock(TOrder order) {
        List<TOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<TOrderItem>()
                .eq(TOrderItem::getOrderId, order.getId()));
        for (TOrderItem oi : items) {
            if (oi.getSkuId() != null) {
                skuMapper.restoreStock(oi.getSkuId(), oi.getQuantity());
                productMapper.syncInventoryFromSkus(oi.getProductId());
            } else {
                productMapper.restoreInventory(oi.getProductId(), oi.getQuantity());
            }
            productMapper.subtractSales(oi.getProductId(), oi.getQuantity());
        }
    }
}