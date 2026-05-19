package com.macro.mall.searchmodern.controller;

import com.macro.mall.searchmodern.api.CommonResult;
import com.macro.mall.searchmodern.domain.EsProductResponse;
import com.macro.mall.searchmodern.domain.OrderAnomalyReport;
import com.macro.mall.searchmodern.domain.ProductRecommendation;
import com.macro.mall.searchmodern.domain.RagResponse;
import com.macro.mall.searchmodern.service.OrderAnomalyService;
import com.macro.mall.searchmodern.service.PersonalizedRecommendationService;
import com.macro.mall.searchmodern.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/ai")
@Tag(name = "AiController", description = "AI 智能客服与推荐")
public class AiController {

    private static final long STREAM_TIMEOUT_MS = 120_000L;

    private final RagService ragService;
    private final PersonalizedRecommendationService recommendationService;
    private final OrderAnomalyService orderAnomalyService;

    public AiController(RagService ragService,
                        PersonalizedRecommendationService recommendationService,
                        OrderAnomalyService orderAnomalyService) {
        this.ragService = ragService;
        this.recommendationService = recommendationService;
        this.orderAnomalyService = orderAnomalyService;
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

    @Operation(summary = "RAG 智能导购：SSE 流式回答")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "用户问题", required = true)
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        CompletableFuture.runAsync(() -> {
            try {
                ragService.askStream(request.question(), new RagService.RagStreamSink() {
                    @Override
                    public void onSources(List<EsProductResponse> sourceProducts) {
                        sendEvent(emitter, "sources", sourceProducts);
                    }

                    @Override
                    public void onToken(String token) {
                        sendEvent(emitter, "token", token);
                    }

                    @Override
                    public void onComplete(RagResponse response) {
                        sendEvent(emitter, "complete", response);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                try {
                    sendEvent(emitter, "error", "抱歉，AI 流式服务调用失败。");
                } catch (Exception ignored) {
                    // Client may have already closed the SSE connection.
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    @Operation(summary = "个性化推荐：基于用户订单商品交互推荐商品")
    @GetMapping("/recommend")
    public CommonResult<List<ProductRecommendation>> recommend(
            @RequestParam Long memberId,
            @RequestParam(required = false, defaultValue = "5") Integer size) {
        return CommonResult.success(recommendationService.recommend(memberId, size));
    }

    @Operation(summary = "订单异常检测：返回异常订单和原因标签")
    @GetMapping("/anomaly/orders")
    public CommonResult<OrderAnomalyReport> detectOrderAnomalies(
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return CommonResult.success(orderAnomalyService.detectOrderAnomalies(days));
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send SSE event", e);
        }
    }
}
