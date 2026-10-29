package com.melody.service.impl;

import com.melody.common.SecurityUtils;
import com.melody.entity.TOperationLog;
import com.melody.mapper.TOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 后台操作日志写入（F-C-007）。所有 admin 写操作 controller 在成功后调用 log.success(...)
 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl {

    private final TOperationLogMapper logMapper;

    /** 便捷写入：模块 / 事件描述 / 操作员自动取当前 admin 用户名 / IP 自动取 request */
    public void log(String module, String event) {
        try {
            TOperationLog log = new TOperationLog();
            log.setModule(module);
            log.setEvent(event);
            log.setOperator(currentOperator());
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                log.setIp(getClientIp(req));
                String ua = req.getHeader("User-Agent");
                if (ua != null && ua.length() > 500) ua = ua.substring(0, 500);
                log.setUserAgent(ua);
            }
            logMapper.insert(log);
        } catch (Exception e) {
            // 日志写入失败不应阻塞主业务
        }
    }

    private String currentOperator() {
        try {
            String name = SecurityUtils.currentUsername();
            return name == null ? "admin" : name;
        } catch (Exception e) {
            return "admin";
        }
    }

    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = req.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = req.getRemoteAddr();
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        return ip;
    }
}