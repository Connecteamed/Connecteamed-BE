package com.connecteamed.server.domain.notification.dto;

import lombok.Builder;

@Builder
public record NotificationRes (
        Long id,
        String notificationType,
        String teamName,
        String message,
        String createdAt,
        boolean isRead,
        String targetUrl
) {}
