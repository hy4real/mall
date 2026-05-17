package com.macro.mall.common.config;

import com.macro.mall.common.domain.SwaggerProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;

public abstract class BaseSwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        SwaggerProperties props = swaggerProperties();
        return new OpenAPI()
                .info(new Info()
                        .title(props.getTitle())
                        .description(props.getDescription())
                        .version(props.getVersion())
                        .contact(new Contact()
                                .name(props.getContactName())
                                .url(props.getContactUrl())
                                .email(props.getContactEmail())))
                .components(new Components()
                        .addSecuritySchemes("Authorization",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("Authorization"));
    }

    public abstract SwaggerProperties swaggerProperties();
}
