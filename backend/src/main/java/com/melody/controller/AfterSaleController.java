package com.melody.controller;

import com.melody.common.R;
import com.melody.common.ResultCode;
import com.melody.common.SecurityUtils;
import com.melody.entity.TAfterSaleTicket;
import com.melody.exception.BizException;
import com.melody.service.IAfterSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台售后工单查询（只能看自己的工单）。
 */
@RestController
@RequestMapping("/api/after-sales")
@RequiredArgsConstructor
public class AfterSaleController {

    private final IAfterSaleService afterSaleService;

    @GetMapping("/tickets")
    public R<List<TAfterSaleTicket>> myTickets() {
        return R.ok(afterSaleService.listByUser(SecurityUtils.currentId()));
    }

    @GetMapping("/tickets/{id}")
    public R<TAfterSaleTicket> detail(@PathVariable Long id) {
        TAfterSaleTicket t = afterSaleService.get(id);
        if (!t.getUserId().equals(SecurityUtils.currentId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看该工单");
        }
        return R.ok(t);
    }
}
