package com.sympsel.utils;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;


public final class PageUtil {
    public static final int MAX_SIZE = 100;

    // 默认每页条数：启动时由 config.json 的 page-size 通过 setDefaultSize 注入（未配置时为 20）
    private static volatile int defaultSize = 20;

    private PageUtil() {
    }

    public static void setDefaultSize(int size) {
        defaultSize = Math.clamp(size, 1, MAX_SIZE);
    }

    public static int getDefaultSize() {
        return defaultSize;
    }

    /**
     * 构建按 sortField 倒序的分页请求。size 为 null 或 <=0 时回退到配置的默认每页条数。
     */
    public static Pageable desc(int page, Integer size, String sortField) {
        int safePage = Math.max(page, 0);
        int resolved = (size == null || size <= 0) ? defaultSize : size;
        int safeSize = Math.clamp(resolved, 1, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, sortField));
    }
}
