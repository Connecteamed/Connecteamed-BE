package com.connecteamed.server.domain.notification.repository;

import com.connecteamed.server.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTypeRepository extends JpaRepository<NotificationType, Long> {
    Optional<NotificationType> findByTypeKey(String typeKey);
}
