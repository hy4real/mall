package com.macro.mall.searchmodern.controller;

import com.macro.mall.searchmodern.api.CommonResult;
import com.macro.mall.searchmodern.domain.RagResponse;
import com.macro.mall.searchmodern.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@Tag(name = "AiController", description = "AI 智能客服与推荐")
public class AiController {

    private final RagService ragService;

    public AiController(RagService ragService) {
        this.ragService = ragService;
    }

    public record ChatRequest(String question) {}

    @Operation(summary = "RAG 智能导购：自然语言提问，AI 基于商品库推荐")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "用户问题", required = true)
    @PostMapping("/chat")
    public CommonResult<RagResponse> chat(@RequestBody ChatRequest request) {
        RagResponse response = ragService.ask(request.question());
        return CommonResult.success(response);
    }
}
