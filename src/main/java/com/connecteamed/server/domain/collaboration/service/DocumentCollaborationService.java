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
        // 1. ID로 조회 (없으면 null 리턴)
        Document doc = documentRepository.findById(Long.parseLong(docId)).orElse(null);
        
        if (doc == null) return null;
        
        // 2. 내용 가져오기
        String content = doc.getContent();

        // 3. 내용이 없으면 "[]" (빈 리스트)를 줘서 프론트엔드 오류 방지
        return (content == null || content.isEmpty()) ? "[]" : content; 
    }

    /**
     * [저장] Redis에 캐시된 문서 데이터(History/Snapshot)를 DB에 최종 반영합니다.
     * 클라이언트로부터 압축된 Yjs 상태(compressedYjs)를 받으면, 기존 content를 덮어쓰는 최적화를 수행합니다.
     * 압축 상태가 없으면, Redis에 쌓인 변경분(newUpdates)을 기존 DB 히스토리에 추가(append)합니다.
     *
     * @param docId 문서 ID
     * @param newUpdates Redis에 캐시된 Yjs 변경분 목록
     * @param latestSnapshot Redis에 캐시된 최신 plain text 스냅샷
     * @param compressedYjs 클라이언트가 보낸 압축된 최종 Yjs 상태
     */
    @Transactional
    public void saveAndFlushHistory(String docId, List<Object> newUpdates, String latestSnapshot, String compressedYjs) {
        // 1. 저장할 게 아무것도 없으면 리턴
        if ((newUpdates == null || newUpdates.isEmpty()) && latestSnapshot == null && compressedYjs == null) return;

        try {
            Document doc = documentRepository.findById(Long.parseLong(docId)).orElseThrow();

            // === 1. 스냅샷(Plain Text) 저장 (사람용 미리보기) ===
            if (latestSnapshot != null) {
                doc.updatePlainText(latestSnapshot);
            }

            // === 2. 히스토리(Yjs) 저장 (기계용 데이터) ===
            if (compressedYjs != null) {
                // ★ [최적화 경로] 클라이언트가 압축된 '한 방'을 줬으므로 덮어씁니다.
                // 기존 리스트를 불러와서 합칠 필요 없이, 그냥 이거 하나만 저장하면 됩니다.
                List<String> optimizedContent = new ArrayList<>();
                optimizedContent.add(compressedYjs);
                
                String jsonContent = objectMapper.writeValueAsString(optimizedContent);
                doc.updateContent(jsonContent);
                
                log.info("Optimized saved for doc {} (Overwritten with 1 compressed state)", docId);

            } else if (newUpdates != null && !newUpdates.isEmpty()) {
                // ★ [일반 경로] 압축 데이터가 없으면 기존 방식대로 '추가(Append)' 합니다.
                List<String> existingHistory = new ArrayList<>();
                String dbContent = doc.getContent();

                if (dbContent != null && !dbContent.isEmpty()) {
                    try {
                        if (dbContent.trim().startsWith("[")) {
                            existingHistory = objectMapper.readValue(dbContent, new TypeReference<List<String>>() {});
                        } 
                    } catch (Exception e) {
                        log.warn("Failed to parse DB history for doc {}. Starting fresh.", docId);
                    }
                }
                
                for (Object update : newUpdates) {
                    existingHistory.add((String) update);
                }
                
                String mergedHistory = objectMapper.writeValueAsString(existingHistory);
                doc.updateContent(mergedHistory);
                
                log.info("Appended history for doc {} (Total size: {})", docId, existingHistory.size());
            }

            // Dirty Checking으로 트랜잭션 종료 시 자동 Commit

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
