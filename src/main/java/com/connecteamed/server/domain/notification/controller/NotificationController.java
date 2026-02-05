package com.connecteamed.server.domain.notification.controller;

import com.connecteamed.server.domain.notification.dto.NotificationListRes;
import com.connecteamed.server.domain.notification.service.NotificationService;
import com.connecteamed.server.global.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "알림 관련 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록 조회", description = "현재 로그인한 사용자의 모든 알림 목록을 최신순으로 조회합니다")
    @GetMapping
    public ResponseEntity<NotificationListRes> getNotifications(Pageable pageable) {
        String loginId = SecurityUtil.getCurrentLoginId();
        return ResponseEntity.ok(notificationService.getNotifications(loginId, pageable));
    }

    @Operation(summary = "알림 삭제", description = "특정 알림을 삭제합니다. 본인의 알림이 아닐 경우 권한 에러가 발생합니다.")
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long notificationId) {
        String loginId = SecurityUtil.getCurrentLoginId();
        notificationService.deleteNotification(notificationId, loginId);
        return ResponseEntity.noContent().build();
    }
}
