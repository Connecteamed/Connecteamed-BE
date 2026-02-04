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
    public void saveAndFlushHistory(String docId, List<Object> newUpdates) {
        if (newUpdates == null || newUpdates.isEmpty()) return;

        try {
            Document doc = documentRepository.findById(Long.parseLong(docId)).orElseThrow();
            
            // 1. 기존 DB 히스토리 가져오기
            List<String> existingHistory = new ArrayList<>();
            String dbContent = doc.getContent();

            if (dbContent != null && !dbContent.isEmpty()) {
                try {
                    // JSON Array 파싱 시도
                    if (dbContent.trim().startsWith("[")) {
                        existingHistory = objectMapper.readValue(dbContent, new TypeReference<List<String>>() {});
                    } 
                } catch (Exception e) {
                    log.warn("Failed to parse DB history for doc {}. Starting fresh.", docId);
                }
            }

            // 2. 새로운 변경분 병합 (Append)
            for (Object update : newUpdates) {
                existingHistory.add((String) update);
            }

            // 3. 다시 JSON으로 변환
            String mergedHistory = objectMapper.writeValueAsString(existingHistory);

            // 4. 엔티티 업데이트 (자동 저장)
            doc.updateContent(mergedHistory);
            
            log.info("Merged history saved for doc {}. Total size: {}", docId, existingHistory.size());

        } catch (Exception e) {
            log.error("Failed to save history for doc {}", docId, e);
            throw new RuntimeException("History Save Failed", e); // 예외를 던져야 롤백됨
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
