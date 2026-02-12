package com.connecteamed.server.domain.retrospective.service;

import com.connecteamed.server.domain.notification.enums.NotificationCategory;
import com.connecteamed.server.domain.notification.service.NotificationCommandService;
import com.connecteamed.server.domain.notification.service.NotificationHelper;
import com.connecteamed.server.domain.retrospective.repository.AiRetrospectiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RetrospectiveAsyncService {
    private final GeminiProvider geminiProvider;
    private final RetrospectiveUpdateService retrospectiveUpdateService;
    private final AiRetrospectiveRepository aiRetrospectiveRepository;
    private final NotificationCommandService notificationCommandService;
    private final NotificationHelper notificationHelper;

    @Async("AsyncExecutor")
    @Transactional
    public void processAiAnalysis(
            Long retrospectiveId,
            String projectName,
            String projectGoal,
            String retrospectiveTitle,
            String totalResult,
            String role,
            String myTaskList,
            String otherTasks
    ) {
        // AI 분석 호출
        String analyzedResult = geminiProvider.getAnalysis(
                projectName, projectGoal, retrospectiveTitle, totalResult, role, myTaskList, otherTasks
        );

        retrospectiveUpdateService.updateRetrospectiveResult(retrospectiveId, analyzedResult);

        aiRetrospectiveRepository.findById(retrospectiveId).ifPresent(retrospective -> {
            notificationHelper.sendToMember(
                    retrospective.getWriter().getMember(),
                    retrospective.getProject(),
                    retrospective.getId(),
                    NotificationCategory.RETROSPECTIVE_COMPLETED
            );
        });
    }
}