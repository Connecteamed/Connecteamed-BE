package com.connecteamed.server.domain.collaboration.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connecteamed.server.domain.document.entity.Document;
import com.connecteamed.server.domain.document.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentCollaborationService {

    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    // ★ 여기에 @Transactional을 붙여야 @Lob(content)을 에러 없이 읽을 수 있습니다.
    @Transactional
    public String getDocumentContent(String docId) {
        Document doc = documentRepository.findById(Long.parseLong(docId)).orElse(null);
        return (doc != null) ? doc.getContent() : null;
    }

    // ★ 저장할 때도 트랜잭션 안에서 안전하게 처리
    @Transactional
    public void saveAndFlushHistory(String docId, List<Object> newUpdates) {
        // ... (아까 작성하신 saveRedisToDb 내부 로직을 여기로 이동) ...
        try {
            Document doc = documentRepository.findById(Long.parseLong(docId)).orElseThrow();
            // ... 기존 JSON 파싱 및 합치기 로직 ...
            doc.updateContent(mergedHistory);
            // Dirty Checking으로 자동 저장됨 (save 호출 불필요)
        } catch (Exception e) {
            // 에러 로그
        }
    }
}
