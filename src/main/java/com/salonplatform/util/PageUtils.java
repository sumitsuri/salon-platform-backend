package com.salonplatform.util;

import com.salonplatform.dto.common.PageResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public final class PageUtils {

    public static final List<Integer> ALLOWED_PAGE_SIZES = List.of(10, 20, 50, 100);
    public static final int DEFAULT_PAGE_SIZE = 20;

    private PageUtils() {}

    public static int normalizeSize(int size) {
        return ALLOWED_PAGE_SIZES.contains(size) ? size : DEFAULT_PAGE_SIZE;
    }

    public static int normalizePage(int page) {
        return Math.max(page, 0);
    }

    public static <T> PageResponse<T> fromPage(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    public static <T> PageResponse<T> slice(List<T> all, int page, int size) {
        int normalizedSize = normalizeSize(size);
        int normalizedPage = normalizePage(page);
        int total = all.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalizedSize);
        int from = Math.min(normalizedPage * normalizedSize, total);
        int to = Math.min(from + normalizedSize, total);
        return PageResponse.<T>builder()
                .content(all.subList(from, to))
                .page(normalizedPage)
                .size(normalizedSize)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }
}
