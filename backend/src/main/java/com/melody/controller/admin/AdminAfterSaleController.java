package com.melody.controller.admin;

import com.melody.common.R;
import com.melody.common.SecurityUtils;
import com.melody.dto.AfterSaleDTOs;
import com.melody.entity.TAfterSaleTicket;
import com.melody.service.IAfterSaleService;
import com.melody.service.impl.OperationLogServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台售后工作台：工单列表、详情、人工审核（通过/拒绝）。
 * 高风险工单由 Python 决策层写入 PENDING_REVIEW，管理员在这里做最终决策。
 */
@RestController
@RequestMapping("/api/admin/after-sales")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
@RequiredArgsConstructor
public class AdminAfterSaleController {

    private final IAfterSaleService afterSaleService;
    private final OperationLogServiceImpl opLog;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('order:read')")
    public R<List<TAfterSaleTicket>> list(
            @RequestParam(required = false, defaultValue = "ALL") String status) {
        return R.ok(afterSaleService.list(status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('order:read')")
    public R<TAfterSaleTicket> detail(@PathVariable Long id) {
        return R.ok(afterSaleService.get(id));
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAuthority('order:refund')")
    public R<TAfterSaleTicket> decide(@PathVariable Long id,
                                      @RequestBody AfterSaleDTOs.DecisionRequest req) {
        TAfterSaleTicket t = afterSaleService.decide(id, req.getAction(), req.getReason(),
                req.getAmount(), SecurityUtils.currentUsername());
        opLog.log("after-sale", "审核售后工单 " + t.getTicketNo() + " → " + t.getStatus());
        return R.ok("已处理", t);
    }
}
