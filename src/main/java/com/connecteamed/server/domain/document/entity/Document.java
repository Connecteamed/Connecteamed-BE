package com.connecteamed.server.domain.document.entity;

import com.connecteamed.server.domain.document.enums.DocumentFileType;
import com.connecteamed.server.domain.project.entity.Project;
import com.connecteamed.server.domain.project.entity.ProjectMember;
import com.connecteamed.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Table(name = "document")
public class Document extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember projectMember;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private DocumentFileType fileType;

    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "plain_text", columnDefinition = "TEXT")
    private String plainText;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID();
        }
    }

    public static Document createText(Project project, ProjectMember projectMember, String title) {
        return Document.builder()
                .project(project)
                .projectMember(projectMember)
                .title(title)
                .fileType(DocumentFileType.TEXT)
                .fileUrl(null)
                .content("[]")
                .plainText("")
                .deletedAt(null)
                .build();
    }

    public static Document createFile(Project project, ProjectMember projectMember, String title, DocumentFileType type, String fileUrl) {
        if (type == DocumentFileType.TEXT) {
            throw new IllegalArgumentException("TEXT 타입은 createFile로 생성할 수 없습니다.");
        }
        return Document.builder()
                .project(project)
                .projectMember(projectMember)
                .title(title)
                .fileType(type)
                .fileUrl(fileUrl)
                .content(null)
                .deletedAt(null)
                .build();
    }

    public void updateTitle(String title) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
    }

    public void updateText(String title, String content) {
        if (this.fileType != DocumentFileType.TEXT) {
            throw new IllegalArgumentException("TEXT 문서만 수정할 수 있습니다.");
        }
        if (title != null) {
            if (title.isBlank()) {
                throw new IllegalArgumentException("제목은 공백일 수 없습니다.");
            }
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
    }

    public void updateContent(String content){
        this.content = content;
    }

    public void updatePlainText(String plainText) {
        this.plainText = plainText;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
