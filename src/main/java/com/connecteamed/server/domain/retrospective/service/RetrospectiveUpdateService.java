package com.connecteamed.server.domain.retrospective.service;

import com.connecteamed.server.domain.retrospective.repository.AiRetrospectiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RetrospectiveUpdateService {
    private final AiRetrospectiveRepository aiRetrospectiveRepository;

    @Transactional
    public void updateRetrospectiveResult(Long retrospectiveId, String analyzedResult) {
        aiRetrospectiveRepository.findById(retrospectiveId).ifPresent(retrospective -> {
            retrospective.update(retrospective.getTitle(), analyzedResult);
        });
    }
}