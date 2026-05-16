package com.macro.mall.searchmodern.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record CommonPage<T>(Integer pageNum, Integer pageSize, Integer totalPage, Long total, List<T> list) {

    public static <T> CommonPage<T> from(Page<T> page) {
        return new CommonPage<>(
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getContent()
        );
    }
}
