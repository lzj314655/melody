package com.melody.controller.internal;

import com.melody.common.R;
import com.melody.common.ResultCode;
import com.melody.dto.AfterSaleDTOs;
import com.melody.entity.TAfterSaleTicket;
import com.melody.exception.BizException;
import com.melody.service.IAfterSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Python LangGraph 服务调用的内部接口。
 *
 * 不依赖用户 JWT，但必须带 X-After-Sale-Token（与后端环境变量一致）；
 * token 为空时接口直接拒绝，避免误配置后裸奔。
 */
@RestController
@RequestMapping("/api/internal/after-sales")
@RequiredArgsConstructor
public class AfterSaleInternalController {

    private final IAfterSaleService afterSaleService;

    @Value("${app.after-sale.internal-token:}")
    private String internalToken;

    private void check(String token) {
        if (internalToken == null || internalToken.isBlank()
                || token == null || !internalToken.equals(token)) {
            throw new BizException(ResultCode.FORBIDDEN, "内部接口鉴权失败");
        }
    }

    @PostMapping("/tickets")
    public R<TAfterSaleTicket> create(@RequestHeader(value = "X-After-Sale-Token", required = false) String token,
                                      @RequestBody AfterSaleDTOs.CreateTicketRequest req) {
        check(token);
        return R.ok(afterSaleService.create(req));
    }

    @GetMapping("/tickets/{id}")
    public R<TAfterSaleTicket> get(@RequestHeader(value = "X-After-Sale-Token", required = false) String token,
                                   @PathVariable Long id) {
        check(token);
        return R.ok(afterSaleService.get(id));
    }

    @PatchMapping("/tickets/{id}")
    public R<TAfterSaleTicket> update(@RequestHeader(value = "X-After-Sale-Token", required = false) String token,
                                      @PathVariable Long id,
                                      @RequestBody AfterSaleDTOs.UpdateTicketRequest req) {
        check(token);
        return R.ok(afterSaleService.update(id, req));
    }

    @PostMapping("/tickets/{id}/events")
    public R<Void> event(@RequestHeader(value = "X-After-Sale-Token", required = false) String token,
                         @PathVariable Long id,
                         @RequestBody AfterSaleDTOs.EventRequest req) {
        check(token);
        afterSaleService.addEvent(id, req.getStep(), req.getActor(), req.getPayload());
        return R.ok();
    }

    /** 自动通过：由 Python 决策层调用，后端再次校验订单状态并执行退款 */
    @PostMapping("/tickets/{id}/refund")
    public R<TAfterSaleTicket> refund(@RequestHeader(value = "X-After-Sale-Token", required = false) String token,
                                      @PathVariable Long id) {
        check(token);
        return R.ok(afterSaleService.refund(id));
    }

    @GetMapping("/risk")
    public R<Map<String, Object>> risk(@RequestHeader(value = "X-After-Sale-Token", required = false) String token,
                                       @RequestParam Long userId,
                                       @RequestParam(required = false) Long productId) {
        check(token);
        return R.ok(afterSaleService.riskFeatures(userId, productId));
    }
}
