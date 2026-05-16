package com.macro.mall.searchmodern.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.macro.mall.searchmodern.dao")
public class MyBatisConfig {
}
