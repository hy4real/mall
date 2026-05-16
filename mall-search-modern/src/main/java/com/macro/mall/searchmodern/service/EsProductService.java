package com.macro.mall.searchmodern.service;

import com.macro.mall.searchmodern.domain.EsProduct;
import com.macro.mall.searchmodern.domain.EsProductRelatedInfo;
import org.springframework.data.domain.Page;

import java.util.List;

public interface EsProductService {
    int importAll();

    void delete(Long id);

    EsProduct create(Long id);

    void delete(List<Long> ids);

    Page<EsProduct> search(String keyword, Integer pageNum, Integer pageSize);

    Page<EsProduct> search(String keyword, Long brandId, Long productCategoryId, Integer pageNum, Integer pageSize, Integer sort);

    Page<EsProduct> recommend(Long id, Integer pageNum, Integer pageSize);

    EsProductRelatedInfo searchRelatedInfo(String keyword);

    Page<EsProduct> searchSemantic(String keyword, Integer pageNum, Integer pageSize);
}
