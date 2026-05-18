package com.macro.mall.searchmodern.domain;

import java.util.List;

public record RagResponse(String answer, List<EsProductResponse> sourceProducts) {
}
