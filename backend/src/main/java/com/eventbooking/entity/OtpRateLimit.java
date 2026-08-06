package com.eventbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_rate_limits", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"email", "purpose"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpRateLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    @Builder.Default
    @Column(nullable = false)
    private int sendCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private int failedAttempts = 0;

    private LocalDateTime lastSentAt;

    // Non-null while locked out; OtpRateLimitService checks this against
    // "now" on every interaction rather than relying on a scheduled job to
    // flip a boolean - avoids the same "state is stale until the job next
    // runs" gap that mattered for events (see EventCompletionJob's comment).
    private LocalDateTime lockedUntil;
}
