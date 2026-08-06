package com.eventbooking.controller;

import com.eventbooking.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately public (see SecurityConfig - permitAll) and separate from
 * /api/admin/analytics/** (which requires ADMIN) - a logged-out visitor
 * browsing events is exactly who this needs to count, so it can't require
 * auth. The frontend calls this once per browser tab session (guarded by
 * sessionStorage), not on every route change.
 */
@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
public class VisitTrackingController {

    private final AnalyticsService analyticsService;

    @PostMapping("/track")
    public ResponseEntity<Void> trackVisit() {
        analyticsService.recordVisit();
        return ResponseEntity.noContent().build();
    }
}
