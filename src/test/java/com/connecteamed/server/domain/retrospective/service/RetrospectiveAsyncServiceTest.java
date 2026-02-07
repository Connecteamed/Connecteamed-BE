package com.connecteamed.server.domain.retrospective.service;

import com.connecteamed.server.domain.retrospective.entity.AiRetrospective;
import com.connecteamed.server.domain.retrospective.repository.AiRetrospectiveRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrospectiveAsyncServiceTest {

    @Mock
    private GeminiProvider geminiProvider;

    @Mock
    private AiRetrospectiveRepository aiRetrospectiveRepository;

    @InjectMocks
    private RetrospectiveAsyncService retrospectiveAsyncService;

    @Test
    @DisplayName("AI 분석 결과가 엔티티에 정상적으로 업데이트되는지 확인")
    void processAiAnalysis_Success() {
        // given
        Long retrospectiveId = 1L;
        String mockAnalysisResult = "STAR 기법으로 정리된 분석 결과입니다.";

        AiRetrospective mockRetrospective = mock(AiRetrospective.class);

        given(geminiProvider.getAnalysis(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(mockAnalysisResult);

        given(aiRetrospectiveRepository.findById(retrospectiveId))
                .willReturn(Optional.of(mockRetrospective));

        // when
        retrospectiveAsyncService.processAiAnalysis(
                retrospectiveId, "프로젝트명", "목표", "회고제목", "전체성과", "역할", "내업무", "팀원업무"
        );

        // then
        // GeminiProvider 호출 여부 확인
        verify(geminiProvider, times(1)).getAnalysis(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        // 엔티티의 update 메서드가 분석 결과와 함께 호출되었는지 확인
        verify(mockRetrospective, times(1)).update(any(), eq(mockAnalysisResult));
    }
}