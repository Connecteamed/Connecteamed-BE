package com.connecteamed.server.domain.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.connecteamed.server.domain.document.dto.DocumentDetailRes;
import com.connecteamed.server.domain.document.dto.DocumentListRes;
import com.connecteamed.server.domain.document.dto.DocumentUpdateTextReq;
import com.connecteamed.server.domain.document.entity.Document;
import com.connecteamed.server.domain.document.enums.DocumentFileType;
import com.connecteamed.server.domain.document.repository.DocumentRepository;
import com.connecteamed.server.domain.member.entity.Member;
import com.connecteamed.server.domain.member.repository.MemberRepository;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.domain.project.repository.ProjectMemberRepository;
import com.connecteamed.server.domain.project.repository.ProjectRepository;
import com.connecteamed.server.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock DocumentRepository documentRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock S3StorageService s3StorageService;
    @Mock MemberRepository memberRepository;

    @InjectMocks DocumentServiceImpl documentService;

    @Test
    @DisplayName("파일 업로드: type이 TEXT면 예외 (S3 호출/저장 없어야 함)")
    void uploadFile_rejectTextType() {
        assertThatThrownBy(() ->
                documentService.uploadFile(1L, "test@example.com", null, DocumentFileType.TEXT)
        ).isInstanceOf(GeneralException.class);

        then(s3StorageService).shouldHaveNoInteractions();
        then(documentRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("문서 다운로드: TEXT 문서는 다운로드 대상이 아니므로 예외")
    void download_rejectTextDocument() {
        Long documentId = 10L;

        Document textDoc = mock(Document.class);
        given(textDoc.getFileType()).willReturn(DocumentFileType.TEXT);

        given(documentRepository.findByIdAndDeletedAtIsNull(documentId))
                .willReturn(Optional.of(textDoc));

        assertThatThrownBy(() -> documentService.download(documentId))
                .isInstanceOf(GeneralException.class);

        then(s3StorageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("문서 상세: TEXT 문서면 content 존재, downloadUrl은 null")
    void detail_text_success() {
        Long documentId = 11L;

        Document d = mock(Document.class);
        given(d.getId()).willReturn(documentId);
        given(d.getTitle()).willReturn("제목");
        given(d.getFileType()).willReturn(DocumentFileType.TEXT);
        given(d.getContent()).willReturn("내용");
        given(d.getCreatedAt()).willReturn(Instant.now());
        given(d.getUpdatedAt()).willReturn(Instant.now());

        given(documentRepository.findByIdAndDeletedAtIsNull(documentId))
                .willReturn(Optional.of(d));

        DocumentDetailRes res = documentService.detail(documentId);

        assertThat(res).isNotNull();
        assertThat(res.content()).isEqualTo("내용");
        assertThat(res.downloadUrl()).isNull();

        then(s3StorageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("문서 상세: 파일 문서면 content는 null, downloadUrl은 존재")
    void detail_file_success() {
        Long documentId = 12L;

        Document d = mock(Document.class);
        given(d.getId()).willReturn(documentId);
        given(d.getTitle()).willReturn("image.png");
        given(d.getFileType()).willReturn(DocumentFileType.IMAGE);
        given(d.getCreatedAt()).willReturn(Instant.now());
        given(d.getUpdatedAt()).willReturn(Instant.now());

        given(documentRepository.findByIdAndDeletedAtIsNull(documentId))
                .willReturn(Optional.of(d));

        DocumentDetailRes res = documentService.detail(documentId);

        assertThat(res).isNotNull();
        assertThat(res.content()).isNull();
        assertThat(res.downloadUrl()).isNotNull();

        then(s3StorageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("문서 상세: 문서가 없으면 예외")
    void detail_notFound() {
        Long documentId = 999L;

        given(documentRepository.findByIdAndDeletedAtIsNull(documentId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.detail(documentId))
                .isInstanceOf(GeneralException.class);

        then(s3StorageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("텍스트 문서 수정: TEXT면 updateText 호출")
    void updateText_success() {
        Long documentId = 1L;
        DocumentUpdateTextReq req = new DocumentUpdateTextReq("수정제목", "수정내용");

        Document d = mock(Document.class);
        given(d.getFileType()).willReturn(DocumentFileType.TEXT);
        given(documentRepository.findByIdAndDeletedAtIsNull(documentId))
                .willReturn(Optional.of(d));

        documentService.updateText(documentId, req);

        then(d).should().updateText("수정제목", "수정내용");
    }

    @Test
    @DisplayName("텍스트 문서 수정: TEXT가 아니면 예외")
    void updateText_rejectNonText() {
        Long documentId = 2L;
        DocumentUpdateTextReq req = new DocumentUpdateTextReq("수정제목", "수정내용");

        Document d = mock(Document.class);
        given(d.getFileType()).willReturn(DocumentFileType.PDF);
        given(documentRepository.findByIdAndDeletedAtIsNull(documentId))
                .willReturn(Optional.of(d));

        assertThatThrownBy(() -> documentService.updateText(documentId, req))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("텍스트 문서 수정: 문서가 없으면 예외")
    void updateText_notFound() {
        Long documentId = 404L;
        DocumentUpdateTextReq req = new DocumentUpdateTextReq("수정제목", "수정내용");

        given(documentRepository.findByIdAndDeletedAtIsNull(documentId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.updateText(documentId, req))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("문서 삭제: 문서가 있으면 softDelete 호출")
    void delete_success() {
        Long documentId = 3L;

        Document d = mock(Document.class);
        given(documentRepository.findByIdAndDeletedAtIsNull(documentId))
                .willReturn(Optional.of(d));

        documentService.delete(documentId);

        then(d).should().softDelete();
    }

    @Test
    @DisplayName("문서 삭제: 문서가 없으면 예외")
    void delete_notFound() {
        Long documentId = 405L;

        given(documentRepository.findByIdAndDeletedAtIsNull(documentId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.delete(documentId))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("문서 목록: TEXT/파일 문서가 응답 DTO로 변환된다")
    void list_success() {
        Long projectId = 1L;
        Instant now = Instant.now();

        // TEXT 문서 mock
        Document textDoc = mock(Document.class);
        given(textDoc.getId()).willReturn(1L);
        given(textDoc.getTitle()).willReturn("텍스트 제목");
        given(textDoc.getFileType()).willReturn(DocumentFileType.TEXT);
        given(textDoc.getCreatedAt()).willReturn(now.minus(2, ChronoUnit.HOURS));

        ProjectMember pm1 = mock(ProjectMember.class);
        Member m1 = mock(Member.class);
        given(m1.getName()).willReturn("멤버1");
        given(pm1.getMember()).willReturn(m1);
        given(textDoc.getProjectMember()).willReturn(pm1);

        // 파일 문서 mock
        Document fileDoc = mock(Document.class);
        given(fileDoc.getId()).willReturn(2L);
        given(fileDoc.getTitle()).willReturn("image.png");
        given(fileDoc.getFileType()).willReturn(DocumentFileType.IMAGE);
        given(fileDoc.getCreatedAt()).willReturn(now.minus(1, ChronoUnit.HOURS));

        ProjectMember pm2 = mock(ProjectMember.class);
        Member m2 = mock(Member.class);
        given(m2.getName()).willReturn("멤버2");
        given(pm2.getMember()).willReturn(m2);
        given(fileDoc.getProjectMember()).willReturn(pm2);

        given(documentRepository.findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId))
                .willReturn(List.of(fileDoc, textDoc));

        DocumentListRes res = documentService.list(projectId);

        assertThat(res).isNotNull();
        assertThat(res.documents()).hasSize(2);

        // createdAt 내림차순 정렬이므로 fileDoc이 먼저 와야 합니다.
        DocumentListRes.Item fileItem = res.documents().get(0);
        assertThat(fileItem.documentId()).isEqualTo(2L);
        assertThat(fileItem.title()).isEqualTo("image.png");
        assertThat(fileItem.type()).isEqualTo(DocumentFileType.IMAGE.name());
        assertThat(fileItem.uploaderName()).isEqualTo("멤버2");
        assertThat(fileItem.downloadUrl()).isNotNull();
        assertThat(fileItem.canEdit()).isFalse();

        DocumentListRes.Item textItem = res.documents().get(1);
        assertThat(textItem.documentId()).isEqualTo(1L);
        assertThat(textItem.title()).isEqualTo("텍스트 제목");
        assertThat(textItem.type()).isEqualTo(DocumentFileType.TEXT.name());
        assertThat(textItem.uploaderName()).isEqualTo("멤버1");
        assertThat(textItem.downloadUrl()).isNull();
        assertThat(textItem.canEdit()).isTrue();

        then(s3StorageService).shouldHaveNoInteractions();
    }
}
