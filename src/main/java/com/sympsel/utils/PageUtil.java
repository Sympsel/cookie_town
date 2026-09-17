package com.sympsel.utils;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;


public final class PageUtil {
    public static final int MAX_SIZE = 100;

    private PageUtil() {
    }

    public static Pageable desc(int page, int size, String sortField) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, sortField));
    }
}
