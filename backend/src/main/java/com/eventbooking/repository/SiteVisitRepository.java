package com.eventbooking.repository;

import com.eventbooking.entity.SiteVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface SiteVisitRepository extends JpaRepository<SiteVisit, Long> {
    long countByVisitedAtAfter(LocalDateTime since);
}
