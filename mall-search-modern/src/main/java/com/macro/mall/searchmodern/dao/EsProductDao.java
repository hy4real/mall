package com.macro.mall.searchmodern.dao;

import com.macro.mall.searchmodern.domain.EsProduct;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EsProductDao {
    List<EsProduct> getAllEsProductList(@Param("id") Long id);
}
