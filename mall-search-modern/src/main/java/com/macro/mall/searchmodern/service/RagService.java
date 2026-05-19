package com.macro.mall.searchmodern.service;

import com.macro.mall.searchmodern.domain.EsProductResponse;
import com.macro.mall.searchmodern.domain.RagResponse;

import java.util.List;

public interface RagService {

    RagResponse ask(String question);

    void askStream(String question, RagStreamSink sink);

    interface RagStreamSink {
        void onSources(List<EsProductResponse> sourceProducts);

        void onToken(String token);

        void onComplete(RagResponse response);
    }
}
