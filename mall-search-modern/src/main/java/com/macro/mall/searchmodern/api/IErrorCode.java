package com.macro.mall.searchmodern.api;

public sealed interface IErrorCode permits ResultCode {
    long code();
    String message();
}
