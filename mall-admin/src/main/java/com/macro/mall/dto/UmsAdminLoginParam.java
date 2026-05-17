package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

/**
 * 用户登录参数
 * Created by macro on 2018/4/26.
 */
public record UmsAdminLoginParam(
    @NotEmpty
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    String username,

    @NotEmpty
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    String password
) {}
