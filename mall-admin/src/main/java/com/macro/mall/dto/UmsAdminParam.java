package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

/**
 * 用户注册参数
 * Created by macro on 2018/4/26.
 */
public record UmsAdminParam(
    @NotEmpty
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    String username,

    @NotEmpty
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    String password,

    @Schema(description = "用户头像")
    String icon,

    @Email
    @Schema(description = "邮箱")
    String email,

    @Schema(description = "用户昵称")
    String nickName,

    @Schema(description = "备注")
    String note
) {}
