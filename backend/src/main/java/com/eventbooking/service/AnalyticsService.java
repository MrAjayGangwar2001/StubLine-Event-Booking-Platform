package com.eventbooking.service;

import com.eventbooking.dto.analytics.AnalyticsOverviewResponse;
import com.eventbooking.dto.analytics.EventAnalyticsResponse;
import com.eventbooking.dto.analytics.RevenueTimelinePoint;
import com.eventbooking.dto.analytics.VisitorCountResponse;
import com.eventbooking.entity.BookingStatus;
import com.eventbooking.entity.Event;
import com.eventbooking.entity.EventStatus;
import com.eventbooking.entity.SeatStatus;
import com.eventbooking.entity.SiteVisit;
import com.eventbooking.repository.BookingRepository;
import com.eventbooking.repository.EventRepository;
import com.eventbooking.repository.EventSeatRepository;
import com.eventbooking.repository.SiteVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * All aggregation happens via SQL (COUNT/SUM/GROUP BY at the repository
 * layer, see BookingRepository/EventSeatRepository), not by loading every
 * booking into memory and summing in Java - the difference matters once
 * there are thousands of bookings, and doing it in the DB is the same
 * "let the database do aggregation" principle as the N+1 fixes elsewhere
 * in this project, just applied to a different kind of query.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final BookingRepository bookingRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventRepository eventRepository;
    private final SiteVisitRepository siteVisitRepository;

    public AnalyticsOverviewResponse getOverview() {
        BigDecimal totalRevenue = bookingRepository.sumTotalAmountByStatus(BookingStatus.CONFIRMED);
        long confirmedBookings = bookingRepository.countByStatus(BookingStatus.CONFIRMED);
        long pendingBookings = bookingRepository.countByStatus(BookingStatus.PENDING);
        long totalEvents = eventRepository.count();
        long upcomingEvents = eventRepository.countByStatus(EventStatus.UPCOMING);

        long seatsSold = eventSeatRepository.countByStatus(SeatStatus.BOOKED);
        long seatsOnSale = eventSeatRepository.count();
        double occupancy = seatsOnSale == 0 ? 0.0 : (seatsSold * 100.0) / seatsOnSale;

        return AnalyticsOverviewResponse.builder()
                .totalRevenue(totalRevenue)
                .totalConfirmedBookings(confirmedBookings)
                .totalPendingBookings(pendingBookings)
                .totalEvents(totalEvents)
                .upcomingEvents(upcomingEvents)
                .overallOccupancyPercent(round(occupancy))
                .build();
    }

    public List<EventAnalyticsResponse> getPerEventBreakdown() {
        List<Event> events = eventRepository.findAll();
        return events.stream().map(this::toEventAnalytics).toList();
    }

    private EventAnalyticsResponse toEventAnalytics(Event event) {
        BigDecimal revenue = bookingRepository.sumTotalAmountByEventIdAndStatus(event.getId(), BookingStatus.CONFIRMED);
        long totalSeats = eventSeatRepository.countByEventId(event.getId());
        long bookedSeats = eventSeatRepository.countByEventIdAndStatus(event.getId(), SeatStatus.BOOKED);
        double occupancy = totalSeats == 0 ? 0.0 : (bookedSeats * 100.0) / totalSeats;

        return EventAnalyticsResponse.builder()
                .eventId(event.getId())
                .title(event.getTitle())
                .revenue(revenue)
                .bookedSeats(bookedSeats)
                .totalSeats(totalSeats)
                .occupancyPercent(round(occupancy))
                .build();
    }

    /**
     * Timeline window is caller-specified (frontend defaults to 30 days) so
     * the dashboard chart isn't hardcoded to one range and the underlying
     * query only ever scans as much history as was actually asked for.
     */
    public List<RevenueTimelinePoint> getBookingsTimeline(int daysBack) {
        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);
        List<Object[]> rows = bookingRepository.findDailyConfirmedBookingsSince(since);

        return rows.stream().map(row -> {
            Date sqlDate = (Date) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal revenue = (BigDecimal) row[2];
            return RevenueTimelinePoint.builder()
                    .date(sqlDate.toLocalDate())
                    .confirmedBookings(count)
                    .revenue(revenue)
                    .build();
        }).toList();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0; // one decimal place, e.g. 73.4%
    }

    // Called once per browser tab session (see VisitTrackingController) -
    // not on every page navigation, so this counts visits/sessions, not
    // page views.
    public void recordVisit() {
        siteVisitRepository.save(SiteVisit.builder().build());
    }

    public VisitorCountResponse getVisitorCount() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        return VisitorCountResponse.builder()
                .totalVisits(siteVisitRepository.count())
                .visitsToday(siteVisitRepository.countByVisitedAtAfter(startOfToday))
                .build();
    }
}
