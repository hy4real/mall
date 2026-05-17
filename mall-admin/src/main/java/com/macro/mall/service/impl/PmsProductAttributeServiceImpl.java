package com.macro.mall.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.dao.PmsProductAttributeDao;
import com.macro.mall.dto.PmsProductAttributeParam;
import com.macro.mall.dto.ProductAttrInfo;
import com.macro.mall.mapper.PmsProductAttributeCategoryMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeCategory;
import com.macro.mall.model.PmsProductAttributeExample;
import com.macro.mall.service.PmsProductAttributeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PmsProductAttributeServiceImpl implements PmsProductAttributeService {
    private final PmsProductAttributeMapper productAttributeMapper;
    private final PmsProductAttributeCategoryMapper productAttributeCategoryMapper;
    private final PmsProductAttributeDao productAttributeDao;

    @Override
    public List<PmsProductAttribute> getList(Long cid, Integer type, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        PmsProductAttributeExample example = new PmsProductAttributeExample();
        example.setOrderByClause("sort desc");
        example.createCriteria().andProductAttributeCategoryIdEqualTo(cid).andTypeEqualTo(type);
        return productAttributeMapper.selectByExample(example);
    }

    @Override
    public int create(PmsProductAttributeParam pmsProductAttributeParam) {
        PmsProductAttribute pmsProductAttribute = new PmsProductAttribute();
        pmsProductAttribute.setProductAttributeCategoryId(pmsProductAttributeParam.productAttributeCategoryId());
        pmsProductAttribute.setName(pmsProductAttributeParam.name());
        pmsProductAttribute.setSelectType(pmsProductAttributeParam.selectType());
        pmsProductAttribute.setInputType(pmsProductAttributeParam.inputType());
        pmsProductAttribute.setInputList(pmsProductAttributeParam.inputList());
        pmsProductAttribute.setSort(pmsProductAttributeParam.sort());
        pmsProductAttribute.setFilterType(pmsProductAttributeParam.filterType());
        pmsProductAttribute.setSearchType(pmsProductAttributeParam.searchType());
        pmsProductAttribute.setRelatedStatus(pmsProductAttributeParam.relatedStatus());
        pmsProductAttribute.setHandAddStatus(pmsProductAttributeParam.handAddStatus());
        pmsProductAttribute.setType(pmsProductAttributeParam.type());
        int count = productAttributeMapper.insertSelective(pmsProductAttribute);
        PmsProductAttributeCategory pmsProductAttributeCategory = productAttributeCategoryMapper.selectByPrimaryKey(pmsProductAttribute.getProductAttributeCategoryId());
        if (pmsProductAttribute.getType() == 0) {
            pmsProductAttributeCategory.setAttributeCount(pmsProductAttributeCategory.getAttributeCount() + 1);
        } else if (pmsProductAttribute.getType() == 1) {
            pmsProductAttributeCategory.setParamCount(pmsProductAttributeCategory.getParamCount() + 1);
        }
        productAttributeCategoryMapper.updateByPrimaryKey(pmsProductAttributeCategory);
        return count;
    }

    @Override
    public int update(Long id, PmsProductAttributeParam productAttributeParam) {
        PmsProductAttribute pmsProductAttribute = new PmsProductAttribute();
        pmsProductAttribute.setId(id);
        pmsProductAttribute.setProductAttributeCategoryId(productAttributeParam.productAttributeCategoryId());
        pmsProductAttribute.setName(productAttributeParam.name());
        pmsProductAttribute.setSelectType(productAttributeParam.selectType());
        pmsProductAttribute.setInputType(productAttributeParam.inputType());
        pmsProductAttribute.setInputList(productAttributeParam.inputList());
        pmsProductAttribute.setSort(productAttributeParam.sort());
        pmsProductAttribute.setFilterType(productAttributeParam.filterType());
        pmsProductAttribute.setSearchType(productAttributeParam.searchType());
        pmsProductAttribute.setRelatedStatus(productAttributeParam.relatedStatus());
        pmsProductAttribute.setHandAddStatus(productAttributeParam.handAddStatus());
        pmsProductAttribute.setType(productAttributeParam.type());
        return productAttributeMapper.updateByPrimaryKeySelective(pmsProductAttribute);
    }

    @Override
    public PmsProductAttribute getItem(Long id) {
        return productAttributeMapper.selectByPrimaryKey(id);
    }

    @Override
    public int delete(List<Long> ids) {
        PmsProductAttribute pmsProductAttribute = productAttributeMapper.selectByPrimaryKey(ids.get(0));
        Integer type = pmsProductAttribute.getType();
        PmsProductAttributeCategory pmsProductAttributeCategory = productAttributeCategoryMapper.selectByPrimaryKey(pmsProductAttribute.getProductAttributeCategoryId());
        PmsProductAttributeExample example = new PmsProductAttributeExample();
        example.createCriteria().andIdIn(ids);
        int count = productAttributeMapper.deleteByExample(example);
        if (type == 0) {
            if (pmsProductAttributeCategory.getAttributeCount() >= count) {
                pmsProductAttributeCategory.setAttributeCount(pmsProductAttributeCategory.getAttributeCount() - count);
            } else {
                pmsProductAttributeCategory.setAttributeCount(0);
            }
        } else if (type == 1) {
            if (pmsProductAttributeCategory.getParamCount() >= count) {
                pmsProductAttributeCategory.setParamCount(pmsProductAttributeCategory.getParamCount() - count);
            } else {
                pmsProductAttributeCategory.setParamCount(0);
            }
        }
        productAttributeCategoryMapper.updateByPrimaryKey(pmsProductAttributeCategory);
        return count;
    }

    @Override
    public List<ProductAttrInfo> getProductAttrInfo(Long productCategoryId) {
        return productAttributeDao.getProductAttrInfo(productCategoryId);
    }
}
