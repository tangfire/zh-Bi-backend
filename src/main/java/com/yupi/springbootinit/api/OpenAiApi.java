package com.yupi.springbootinit.api;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONUtil;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class OpenAiApi {

    /**
     * AI 对话（需要自己创建请求响应对象）
     *
     * @param request
     * @param openAiApiKey
     * @return
     */
    public CreateChatCompletionResponse createChatCompletion(CreateChatCompletionRequest request, String openAiApiKey) {
        if (StringUtils.isBlank(openAiApiKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未传 openAiApiKey");
        }
        String url = "https://api.openai.com/v1/chat/completions";
        String json = JSONUtil.toJsonStr(request);
        String result = HttpRequest.post(url)
                .header("Authorization", "Bearer " + openAiApiKey)
                .body(json)
                .execute()
                .body();
        return JSONUtil.toBean(result, CreateChatCompletionResponse.class);
    }

    @Data
    public static class CreateChatCompletionRequest {
        private String model = "gpt-3.5-turbo";
        private List<Message> messages;
        private Double temperature = 0.7;

        @Data
        public static class Message {
            private String role;
            private String content;
        }
    }

    @Data
    public static class CreateChatCompletionResponse {
        private String id;
        private String object;
        private long created;
        private List<Choice> choices;
        private Usage usage;

        @Data
        public static class Choice {
            private int index;
            private Message message;
            private String finish_reason;
        }

        @Data
        public static class Message {
            private String role;
            private String content;
        }

        @Data
        public static class Usage {
            private int prompt_tokens;
            private int completion_tokens;
            private int total_tokens;
        }
    }


}
