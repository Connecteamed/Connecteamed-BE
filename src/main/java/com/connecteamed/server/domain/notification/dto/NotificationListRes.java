package com.connecteamed.server.domain.notification.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record NotificationListRes (
      long unreadCount,
      List<NotificationRes> notifications
) {}
