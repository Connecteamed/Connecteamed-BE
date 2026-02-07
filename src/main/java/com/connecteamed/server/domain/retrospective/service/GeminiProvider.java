package com.connecteamed.server.domain.retrospective.service;

import com.connecteamed.server.domain.retrospective.dto.GeminiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GeminiProvider {

    @Value("${GEMINI_API_KEY}")
    private String apiKey;

    private final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private final RestTemplate restTemplate;
    private final ResourceLoader resourceLoader;

    public String getAnalysis(
            String projectName,
            String projectGoal,
            String retrospectiveTitle,
            String totalResult,
            String role,
            String myTaskList,
            String otherTasks
    ) {
        String url = API_URL + "?key=" + apiKey;

        try {
            // 1. 외부 마크다운 파일 읽기
            Resource resource = resourceLoader.getResource("classpath:prompts/ai-retrospective.md");
            String template = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

            // 2. 템플릿의 %s 자리에 데이터 매핑
            String prompt = String.format(template,
                    projectName, projectGoal, retrospectiveTitle, totalResult, role, myTaskList, otherTasks,
                    retrospectiveTitle, otherTasks, role, myTaskList, myTaskList, totalResult
            );

            // 3. API 요청 생성
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
            );

            // 4. 호출 및 결과 반환
            GeminiResponse response = restTemplate.postForObject(url, requestBody, GeminiResponse.class);
            if (response != null && !response.candidates().isEmpty()) {
                return response.candidates().get(0).content().parts().get(0).text();
            }
            return "분석 결과를 가져오지 못했습니다.";

        } catch (Exception e) {
            return "프롬프트 파일을 읽거나 AI 분석 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}