package com.connecteamed.server.domain.document.service;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.connecteamed.server.domain.document.dto.DocumentCreateRes;
import com.connecteamed.server.domain.document.dto.DocumentCreateTextReq;
import com.connecteamed.server.domain.document.dto.DocumentDetailRes;
import com.connecteamed.server.domain.document.dto.DocumentListRes;
import com.connecteamed.server.domain.document.dto.DocumentUpdateTextReq;
import com.connecteamed.server.domain.document.dto.DocumentUploadRes;
import com.connecteamed.server.domain.document.entity.Document;
import com.connecteamed.server.domain.document.enums.DocumentFileType;
import com.connecteamed.server.domain.document.repository.DocumentRepository;
import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.global.apiPayload.code.GeneralErrorCode;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final S3StorageService s3StorageService;
    private final MemberRepository memberRepository;

    //문서 목록 조회
    @Override
    @Transactional(readOnly = true)
    public DocumentListRes list(Long projectId) {
        List<Document> docs = documentRepository.findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId);

        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy.MM.dd")
                .withZone(ZoneId.of("Asia/Seoul"));

        return new DocumentListRes(
                docs.stream()
                        .map(d -> new DocumentListRes.Item(
                                d.getId(),
                                d.getTitle(),
                                d.getFileType().name(),
                                d.getProjectMember().getMember().getName(),
                                df.format(d.getCreatedAt()),
                                (d.getFileType() != DocumentFileType.TEXT)
                                        ? "/api/documents/" + d.getId() + "/download"
                                        : null,
                                d.getFileType() == DocumentFileType.TEXT
                        ))
                        .toList()
        );
    }

    //문서 상세 조회
    @Override
    @Transactional(readOnly = true)
    public DocumentDetailRes detail(Long documentId) {
        Document d = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다."));

        return new DocumentDetailRes(
                d.getId(),
                d.getTitle(),
                d.getFileType().name(),
                (d.getFileType() == DocumentFileType.TEXT) ? d.getContent() : null,
                (d.getFileType() != DocumentFileType.TEXT) ? "/api/documents/" + d.getId() + "/download" : null,
                d.getCreatedAt().toString(),
                d.getUpdatedAt().toString()
        );
    }

    //문서 다운로드
    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(Long documentId) {
        Document d = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다."));

        if (d.getFileType() == DocumentFileType.TEXT) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "TEXT 문서는 다운로드 대상이 아닙니다.");
        }
        if (d.getFileUrl() == null || d.getFileUrl().isBlank()) {
            throw new GeneralException(GeneralErrorCode.NOT_FOUND, "파일 key가 없습니다.");
        }

        String key = d.getFileUrl();

        try {
            InputStream in = s3StorageService.download(key);
            Resource resource = new InputStreamResource(in);

            // 다운로드 파일명: DB title 우선, 없으면 key에서 파일명 뽑기
            String filename = (d.getTitle() != null && !d.getTitle().isBlank())
                    ? d.getTitle()
                    : key.substring(key.lastIndexOf('/') + 1);

            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                    .body(resource);

        } catch (Exception e) {
            throw new GeneralException(GeneralErrorCode.NOT_FOUND, "파일 다운로드에 실패했습니다.");
        }
    }

    //문서 추가(텍스트)
    @Override
    @Transactional
    public DocumentCreateRes createText(Long projectId, String loginId, DocumentCreateTextReq req) {
        Project projectRef = projectRepository.getReferenceById(projectId);

        ProjectMember projectMember = getProjectMember(projectId, loginId);

        Document d = Document.createText(projectRef, projectMember, req.title());
        documentRepository.save(d);

        return new DocumentCreateRes(d.getId(), d.getCreatedAt().toString());
    }

    //문서 추가(파일)
    @Override
    @Transactional
    public DocumentUploadRes uploadFile(Long projectId, String loginId, MultipartFile file, DocumentFileType type) {
        if (type == DocumentFileType.TEXT) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "TEXT는 파일 업로드 타입이 아닙니다.");
        }

        Project projectRef = projectRepository.getReferenceById(projectId);

        ProjectMember projectMember = getProjectMember(projectId, loginId);

        String fileUrl = s3StorageService.upload(file, "project-" + projectId);

        String title = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                ? "file"
                : file.getOriginalFilename();

        Document d = Document.createFile(projectRef, projectMember, title, type, fileUrl);
        documentRepository.save(d);

        return new DocumentUploadRes(d.getId(), title, d.getCreatedAt().toString());
    }

    //문서 수정(텍스트)
    @Override
    @Transactional
    public void updateText(Long documentId , DocumentUpdateTextReq req) {
        Document d = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다."));

        if (d.getFileType() != DocumentFileType.TEXT) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "텍스트 문서만 수정할 수 있습니다.");
        }

        d.updateText(req.title(), req.content());
    }

    //문서 삭제
    @Override
    @Transactional
    public void delete(Long documentId) {
        Document d = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다."));

        // 파일 문서면 필요 시 S3 삭제 정책 추가 가능
        // if (d.getFileType() != DocumentFileType.TEXT) { ... }

        d.softDelete();
    }

    private ProjectMember getProjectMember(Long projectId, String loginId) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));
        log.info("[DocumentService] Member found: id={}, name={}", member.getId(), member.getName());

        return projectMemberRepository
                .findByProject_IdAndMember_Id(projectId, member.getId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 프로젝트의 멤버가 아닙니다."));
    }
}
