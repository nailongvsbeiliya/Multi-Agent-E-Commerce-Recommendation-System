package com.ecommerce.config;

import com.ecommerce.MultiAgentApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MultiAgentApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder chatClientBuilder;

    @BeforeEach
    void setUpLlmMocks() {
        when(chatClientBuilder.build().prompt().system(anyString()).user(startsWith("用户ID")).call().content())
                .thenReturn("高活跃用户，偏好手机和耳机，对价格较敏感。");
        when(chatClientBuilder.build().prompt().user(startsWith("请根据用户偏好类目")).call().content())
                .thenReturn("[\"P001\",\"P003\",\"P007\"]");
        when(chatClientBuilder.build().prompt().system(anyString()).user(startsWith("商品列表")).call().content())
                .thenReturn("""
                        [
                          {"product_id":"P001","copy":"iPhone 16 Pro 很贴合你当前的浏览偏好，体验和性能都很均衡。"},
                          {"product_id":"P003","copy":"AirPods Pro 3 兼顾音质与便携，适合日常通勤和影音场景。"},
                          {"product_id":"P007","copy":"Anker 140W 充电器补能高效，作为手机配件搭配购买很合适。"}
                        ]
                        """);
    }

    @Test
    void recommendReturnsPersonalizedResponse() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "userId", "u001",
                "scene", "homepage",
                "numItems", 3,
                "context", Map.of("scene", "homepage")
        ));

        mockMvc.perform(post("/api/v1/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("u001")))
                .andExpect(jsonPath("$.products", hasSize(3)))
                .andExpect(jsonPath("$.products[*].productId", hasItem("P001")))
                .andExpect(jsonPath("$.products[*].productId", hasItem("P002")))
                .andExpect(jsonPath("$.products[*].productId", hasItem("P004")))
                .andExpect(jsonPath("$.marketingCopies", hasSize(3)))
                .andExpect(jsonPath("$.marketingCopies[0].product_id", is("P001")))
                .andExpect(jsonPath("$.marketingCopies[*].product_id", hasItem("P003")))
                .andExpect(jsonPath("$.experimentGroup").exists())
                .andExpect(jsonPath("$.experimentInfo.experiment_id", is("rec_strategy")))
                .andExpect(jsonPath("$.agentResults.user_profile.success", is(true)))
                .andExpect(jsonPath("$.agentResults.product_recall.success", is(true)))
                .andExpect(jsonPath("$.agentResults.inventory.success", is(true)))
                .andExpect(jsonPath("$.purchaseLimits").exists())
                .andExpect(jsonPath("$.totalLatencyMs").isNumber());
    }

    @Test
    void recommendChatEndpointSupportsNaturalLanguageRequest() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "userId", "u009",
                "query", "I want noise cancelling headphones, recommend 3 items",
                "numItems", 3
        ));

        mockMvc.perform(post("/api/v1/recommend/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("u009")))
                .andExpect(jsonPath("$.products", hasSize(3)))
                .andExpect(jsonPath("$.products[*].productId", hasItem("P003")))
                .andExpect(jsonPath("$.marketingCopies", hasSize(3)))
                .andExpect(jsonPath("$.experimentGroup").exists())
                .andExpect(jsonPath("$.agentResults.product_recall.success", is(true)));
    }

    @Test
    void recommendChatEndpointRespectsNegativeIntent() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "userId", "u010",
                "query", "不要苹果手机，推荐3款",
                "numItems", 3
        ));

        mockMvc.perform(post("/api/v1/recommend/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("u010")))
                .andExpect(jsonPath("$.products", hasSize(3)))
                .andExpect(jsonPath("$.products[*].productId", not(hasItem("P001"))))
                .andExpect(jsonPath("$.products[*].productId", not(hasItem("P003"))))
                .andExpect(jsonPath("$.agentResults.product_recall.success", is(true)));
    }
}
