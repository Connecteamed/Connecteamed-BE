package com.connecteamed.server.domain.contribution.service;

import com.connecteamed.server.domain.contribution.dto.ContributionReq;
import com.connecteamed.server.domain.contribution.dto.ContributionRes;
import com.connecteamed.server.domain.contribution.entity.Contribution;
import com.connecteamed.server.domain.contribution.enums.ContributionAction;
import com.connecteamed.server.domain.contribution.repository.ContributionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ContributionServiceTest {

    @InjectMocks
    private ContributionService contributionService;

    @Mock
    private ContributionRepository contributionRepository;

    @Test
    @DisplayName("활동 기록 성공 - 새로운 활동인 경우 DB에 저장하고 증분 여부 true를 반환한다")
    void recordContribution_success() {
        // given
        Long userId = 1L;
        ContributionReq request = new ContributionReq(ContributionAction.TASK_CREATE, 100L);

        given(contributionRepository.existsByUserIdAndActionTypeAndTargetId(userId, request.actionType(), request.targetId()))
                .willReturn(false);
        given(contributionRepository.countTodayActivities(eq(userId), any(Instant.class), any(Instant.class)))
                .willReturn(1);

        // when
        ContributionRes result = contributionService.recordContribution(userId, request);

        // then
        assertThat(result.isIncremented()).isTrue();
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.currentLevel()).isEqualTo(1); // 1회는 레벨 1
        verify(contributionRepository, times(1)).save(any(Contribution.class));
    }

    @Test
    @DisplayName("활동 기록 중복 - 이미 존재하는 targetId인 경우 저장하지 않고 증분 여부 false를 반환한다")
    void recordContribution_duplicate() {
        // given
        Long userId = 1L;
        ContributionReq request = new ContributionReq(ContributionAction.TASK_CREATE, 100L);

        given(contributionRepository.existsByUserIdAndActionTypeAndTargetId(userId, request.actionType(), request.targetId()))
                .willReturn(true);
        given(contributionRepository.countTodayActivities(eq(userId), any(Instant.class), any(Instant.class)))
                .willReturn(5);

        // when
        ContributionRes result = contributionService.recordContribution(userId, request);

        // then
        assertThat(result.isIncremented()).isFalse();
        assertThat(result.totalCount()).isEqualTo(5);
        assertThat(result.currentLevel()).isEqualTo(2);
        verify(contributionRepository, never()).save(any(Contribution.class));
    }

    @Test
    @DisplayName("잔디 레벨 계산 검증 - 횟수에 따라 0~4 단계가 정확히 반환된다")
    void calculateLevel_test() {

        assertThat(getLevelFromCount(0)).isEqualTo(0);
        assertThat(getLevelFromCount(1)).isEqualTo(1);
        assertThat(getLevelFromCount(3)).isEqualTo(2);
        assertThat(getLevelFromCount(6)).isEqualTo(3);
        assertThat(getLevelFromCount(10)).isEqualTo(4);
    }

    private int getLevelFromCount(int count) {
        Long userId = 1L;
        ContributionReq req = new ContributionReq(ContributionAction.TASK_CREATE, 999L);

        lenient().when(contributionRepository.existsByUserIdAndActionTypeAndTargetId(anyLong(), any(), anyLong()))
                .thenReturn(true);
        lenient().when(contributionRepository.countTodayActivities(eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(count);

        return contributionService.recordContribution(userId, req).currentLevel();
    }
}
