package com.melody.common;

import lombok.Data;

/**
 * 统一分页请求参数
 *
 * 用法:Controller 接收 PageQuery + 各业务自己的查询条件,
 *      service 转成 MyBatis-Plus 的 Page&lt;T&gt;
 */
@Data
public class PageQuery {
    /** 页码(从 1 开始) */
    private Long page = 1L;
    /** 每页大小(默认 10,上限 100) */
    private Long size = 10L;

    /** 防越界:小于 1 视作 1 */
    public Long safePage() {
        return page == null || page < 1 ? 1L : page;
    }
    public Long safeSize() {
        long s = size == null || size < 1 ? 10L : size;
        return s > 100 ? 100 : s;
    }
}