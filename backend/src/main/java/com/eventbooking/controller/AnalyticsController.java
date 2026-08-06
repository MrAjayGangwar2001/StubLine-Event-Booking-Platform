package com.eventbooking.controller;

import com.eventbooking.dto.analytics.AnalyticsOverviewResponse;
import com.eventbooking.dto.analytics.EventAnalyticsResponse;
import com.eventbooking.dto.analytics.RevenueTimelinePoint;
import com.eventbooking.dto.analytics.VisitorCountResponse;
import com.eventbooking.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsOverviewResponse> getSummary() {
        return ResponseEntity.ok(analyticsService.getOverview());
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventAnalyticsResponse>> getPerEventBreakdown() {
        return ResponseEntity.ok(analyticsService.getPerEventBreakdown());
    }

    @GetMapping("/bookings-timeline")
    public ResponseEntity<List<RevenueTimelinePoint>> getBookingsTimeline(
            @RequestParam(defaultValue = "30") int daysBack) {
        return ResponseEntity.ok(analyticsService.getBookingsTimeline(daysBack));
    }

    // Overrides the class-level @PreAuthorize("hasRole('ADMIN')") with a
    // stricter one - Spring Security applies the most specific
    // (method-level) annotation when both are present, so this endpoint
    // requires SUPER_ADMIN even though everything else in this controller
    // only requires ADMIN.
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/visitor-count")
    public ResponseEntity<VisitorCountResponse> getVisitorCount() {
        return ResponseEntity.ok(analyticsService.getVisitorCount());
    }
}
