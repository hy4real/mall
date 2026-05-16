package com.macro.mall.searchmodern.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringdocConfig {

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("mall搜索系统")
                        .description("mall搜索相关接口文档（Spring Boot 3 + ES 8 现代化版）")
                        .version("1.0")
                        .contact(new Contact().name("macro")));
    }
}
