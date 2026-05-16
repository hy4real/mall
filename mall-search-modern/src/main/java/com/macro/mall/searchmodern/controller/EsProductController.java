package com.macro.mall.searchmodern.controller;

import com.macro.mall.searchmodern.api.CommonPage;
import com.macro.mall.searchmodern.api.CommonResult;
import com.macro.mall.searchmodern.domain.EsProduct;
import com.macro.mall.searchmodern.domain.EsProductRelatedInfo;
import com.macro.mall.searchmodern.service.EsProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/esProduct")
@Tag(name = "EsProductController", description = "搜索商品管理")
public class EsProductController {

    private final EsProductService esProductService;

    public EsProductController(EsProductService esProductService) {
        this.esProductService = esProductService;
    }

    @Operation(summary = "导入所有数据库中商品到ES")
    @PostMapping("/importAll")
    public CommonResult<Integer> importAllList() {
        int count = esProductService.importAll();
        return CommonResult.success(count);
    }

    @Operation(summary = "根据id删除商品")
    @GetMapping("/delete/{id}")
    public CommonResult<Void> delete(@PathVariable Long id) {
        esProductService.delete(id);
        return CommonResult.success(null);
    }

    @Operation(summary = "根据id批量删除商品")
    @PostMapping("/delete/batch")
    public CommonResult<Void> delete(@RequestParam("ids") List<Long> ids) {
        esProductService.delete(ids);
        return CommonResult.success(null);
    }

    @Operation(summary = "根据id创建商品")
    @PostMapping("/create/{id}")
    public CommonResult<EsProduct> create(@PathVariable Long id) {
        EsProduct esProduct = esProductService.create(id);
        if (esProduct != null) {
            return CommonResult.success(esProduct);
        }
        return CommonResult.failed();
    }

    @Operation(summary = "简单搜索")
    @GetMapping("/search/simple")
    public CommonResult<CommonPage<EsProduct>> search(
            @Parameter(description = "关键字") @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") Integer pageNum,
            @RequestParam(required = false, defaultValue = "5") Integer pageSize) {
        Page<EsProduct> esProductPage = esProductService.search(keyword, pageNum, pageSize);
        return CommonResult.success(CommonPage.from(esProductPage));
    }

    @Operation(summary = "综合搜索、筛选、排序")
    @GetMapping("/search")
    public CommonResult<CommonPage<EsProduct>> search(
            @Parameter(description = "关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "品牌ID") @RequestParam(required = false) Long brandId,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long productCategoryId,
            @RequestParam(required = false, defaultValue = "0") Integer pageNum,
            @RequestParam(required = false, defaultValue = "5") Integer pageSize,
            @Parameter(description = "排序:0->相关度;1->新品;2->销量;3->价格升;4->价格降")
            @RequestParam(required = false, defaultValue = "0") Integer sort) {
        Page<EsProduct> esProductPage = esProductService.search(keyword, brandId, productCategoryId, pageNum, pageSize, sort);
        return CommonResult.success(CommonPage.from(esProductPage));
    }

    @Operation(summary = "根据商品id推荐商品")
    @GetMapping("/recommend/{id}")
    public CommonResult<CommonPage<EsProduct>> recommend(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer pageNum,
            @RequestParam(required = false, defaultValue = "5") Integer pageSize) {
        Page<EsProduct> esProductPage = esProductService.recommend(id, pageNum, pageSize);
        return CommonResult.success(CommonPage.from(esProductPage));
    }

    @Operation(summary = "获取搜索的相关品牌、分类及筛选属性")
    @GetMapping("/search/relate")
    public CommonResult<EsProductRelatedInfo> searchRelatedInfo(
            @Parameter(description = "关键字") @RequestParam(required = false) String keyword) {
        EsProductRelatedInfo relatedInfo = esProductService.searchRelatedInfo(keyword);
        return CommonResult.success(relatedInfo);
    }

    @Operation(summary = "语义搜索（BM25 + 向量混合）")
    @GetMapping("/search/semantic")
    public CommonResult<CommonPage<EsProduct>> searchSemantic(
            @Parameter(description = "关键字") @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "0") Integer pageNum,
            @RequestParam(required = false, defaultValue = "5") Integer pageSize) {
        Page<EsProduct> esProductPage = esProductService.searchSemantic(keyword, pageNum, pageSize);
        return CommonResult.success(CommonPage.from(esProductPage));
    }
}
