package com.localrag.agent.contract;

import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface LlmProvider {

    record StreamHandle(Disposable subscription, Runnable onCancel) {
        public void cancel() {
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }

    StreamHandle streamChat(String userId,
                            List<Map<String, Object>> messages,
                            List<Map<String, Object>> tools,
                            int maxTokens,
                            Consumer<String> onChunk,
                            Consumer<Throwable> onError,
                            Runnable onComplete);

    String getName();
}
