package com.eventbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Admin-managed banner shown on the public events/home page - "server
 * maintenance tonight", "20% off this weekend", "site under partial outage",
 * etc. Deliberately a plain list rather than a single-row settings table:
 * an admin can prep the next announcement while one is still active, and
 * toggling `active` is how one gets shown/hidden, not deleting/recreating.
 */
@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AnnouncementSeverity severity = AnnouncementSeverity.INFO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
