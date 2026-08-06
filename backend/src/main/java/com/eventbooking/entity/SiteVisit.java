package com.eventbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per tracked visit (see VisitTrackingController - the frontend
 * calls this once per browser tab session, not on every page navigation).
 * Deliberately a plain event log rather than a single running counter
 * column: a log lets "visits today" / "visits this week" be a simple COUNT
 * query with a date filter, instead of needing separate counters
 * maintained for every time window someone might want later.
 */
@Entity
@Table(name = "site_visits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime visitedAt;

    @PrePersist
    protected void onCreate() {
        this.visitedAt = LocalDateTime.now();
    }
}
