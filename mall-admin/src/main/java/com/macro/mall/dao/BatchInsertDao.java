package com.macro.mall.dao;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BatchInsertDao<T> {
    int insertList(@Param("list") List<T> list);
}
