package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

/**
 * 修改用户名密码参数
 * Created by macro on 2019/10/9.
 */
public record UpdateAdminPasswordParam(
    @NotEmpty
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    String username,

    @NotEmpty
    @Schema(description = "旧密码", requiredMode = Schema.RequiredMode.REQUIRED)
    String oldPassword,

    @NotEmpty
    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED)
    String newPassword
) {}
