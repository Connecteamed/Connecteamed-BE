package com.connecteamed.server.domain.contribution.entity;

import com.connecteamed.server.domain.contribution.enums.ContributionAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "contributions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contribution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContributionAction actionType;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private Instant createdAt;

    @Builder
    public Contribution(Long userId, ContributionAction actionType, Long targetId) {
        this.userId = userId;
        this.actionType = actionType;
        this.targetId = targetId;
        this.createdAt = Instant.now();
    }
}
