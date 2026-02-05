package com.connecteamed.server.domain.collaboration.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connecteamed.server.domain.document.entity.Document;
import com.connecteamed.server.domain.document.repository.DocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentCollaborationService {

    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    /**
     * [조회] DB에 저장된 Yjs 히스토리(content)를 가져옴
     * @Transactional: @Lob 필드 조회 시 필수
     */
    @Transactional(readOnly = true)
    public String getDocumentContent(String docId) {
        // ID 파싱 에러 방지를 위해 try-catch 혹은 Long 변환 주의
        Document doc = documentRepository.findById(Long.parseLong(docId)).orElse(null);
        return (doc != null) ? doc.getContent() : null;
    }

    /**
     * [저장] Redis의 변경분(newUpdates)을 기존 DB 히스토리와 병합하여 저장
     * @Transactional: 트랜잭션 범위 안에서 Dirty Checking으로 저장
     */
    @Transactional
    public void saveAndFlushHistory(String docId, List<Object> newUpdates, String latestSnapshot) {
        // 1. 업데이트할 게 없으면 리턴
        if ((newUpdates == null || newUpdates.isEmpty()) && latestSnapshot == null) return;

        try {
            Document doc = documentRepository.findById(Long.parseLong(docId)).orElseThrow();
            
            // === A. 히스토리(Yjs) 병합 저장 ===
            if (newUpdates != null && !newUpdates.isEmpty()) {
                List<String> existingHistory = new ArrayList<>();
                String dbContent = doc.getContent();

                if (dbContent != null && !dbContent.isEmpty()) {
                    try {
                        if (dbContent.trim().startsWith("[")) {
                            existingHistory = objectMapper.readValue(dbContent, new TypeReference<List<String>>() {});
                        } 
                    } catch (Exception e) {
                        log.warn("Failed to parse DB history for doc {}.", docId);
                    }
                }
                for (Object update : newUpdates) {
                    existingHistory.add((String) update);
                }
                String mergedHistory = objectMapper.writeValueAsString(existingHistory);
                doc.updateContent(mergedHistory);
            }

            // === B. ★ [추가] 스냅샷(Plain Text) 저장 ===
            // Redis에 저장된 최신 텍스트가 있다면 DB에 반영
            if (latestSnapshot != null) {
                doc.updatePlainText(latestSnapshot);
            }
            
            // Dirty Checking으로 commit 시점에 자동 저장됨

        } catch (Exception e) {
            log.error("Failed to save doc {}", docId, e);
            throw new RuntimeException("Save Failed", e);
        }
    }

    /**
     * [스냅샷 저장] 완성된 텍스트(plain_text)를 저장
     */
    @Transactional
    public void savePlainTextSnapshot(String docId, String plainText) {
        try {
            Document doc = documentRepository.findById(Long.parseLong(docId)).orElseThrow();
            doc.updatePlainText(plainText);
            // dirty checking으로 자동 저장됨
            log.info("Saved plain text snapshot for doc {}", docId);
        } catch (Exception e) {
            log.error("Failed to save snapshot for doc {}", docId, e);
        }
    }
}
