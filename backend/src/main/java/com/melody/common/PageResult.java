package com.melody.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一分页响应
 *
 * 字段:records 当前页数据 / total 总条数 / page 当前页 / size 每页大小 / totalPages 总页数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long page;
    private long size;
    private long totalPages;

    /** 从 MyBatis-Plus Page 构造 */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.core.metadata.IPage<T> p) {
        return new PageResult<>(
                p.getRecords(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize(),
                p.getPages()
        );
    }
}