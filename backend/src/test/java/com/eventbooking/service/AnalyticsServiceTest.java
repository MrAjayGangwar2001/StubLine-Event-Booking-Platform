package com.eventbooking.service;

import com.eventbooking.entity.BookingStatus;
import com.eventbooking.entity.Event;
import com.eventbooking.entity.EventStatus;
import com.eventbooking.entity.SeatStatus;
import com.eventbooking.repository.BookingRepository;
import com.eventbooking.repository.EventRepository;
import com.eventbooking.repository.EventSeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private EventSeatRepository eventSeatRepository;
    @Mock private EventRepository eventRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getOverview_computesOccupancyAsPercentOfSeatsSoldOverSeatsOnSale() {
        when(bookingRepository.sumTotalAmountByStatus(BookingStatus.CONFIRMED)).thenReturn(new BigDecimal("45000"));
        when(bookingRepository.countByStatus(BookingStatus.CONFIRMED)).thenReturn(30L);
        when(bookingRepository.countByStatus(BookingStatus.PENDING)).thenReturn(4L);
        when(eventRepository.count()).thenReturn(3L);
        when(eventRepository.countByStatus(EventStatus.UPCOMING)).thenReturn(2L);
        when(eventSeatRepository.countByStatus(SeatStatus.BOOKED)).thenReturn(150L);
        when(eventSeatRepository.count()).thenReturn(200L);

        var overview = analyticsService.getOverview();

        assertThat(overview.getTotalRevenue()).isEqualByComparingTo("45000");
        assertThat(overview.getTotalConfirmedBookings()).isEqualTo(30L);
        assertThat(overview.getTotalPendingBookings()).isEqualTo(4L);
        assertThat(overview.getTotalEvents()).isEqualTo(3L);
        assertThat(overview.getUpcomingEvents()).isEqualTo(2L);
        assertThat(overview.getOverallOccupancyPercent()).isEqualTo(75.0); // 150/200 = 75%
    }

    @Test
    void getOverview_returnsZeroOccupancy_whenNoSeatsHaveBeenPutOnSaleYet() {
        when(bookingRepository.sumTotalAmountByStatus(BookingStatus.CONFIRMED)).thenReturn(BigDecimal.ZERO);
        when(bookingRepository.countByStatus(BookingStatus.CONFIRMED)).thenReturn(0L);
        when(bookingRepository.countByStatus(BookingStatus.PENDING)).thenReturn(0L);
        when(eventRepository.count()).thenReturn(0L);
        when(eventRepository.countByStatus(EventStatus.UPCOMING)).thenReturn(0L);
        when(eventSeatRepository.countByStatus(SeatStatus.BOOKED)).thenReturn(0L);
        when(eventSeatRepository.count()).thenReturn(0L);

        var overview = analyticsService.getOverview();

        // Division by zero must not throw or return NaN/Infinity - a brand new
        // platform with no events yet should just show 0%, not crash the dashboard.
        assertThat(overview.getOverallOccupancyPercent()).isEqualTo(0.0);
    }

    @Test
    void getPerEventBreakdown_computesOccupancyPerEventIndependently() {
        Event event = Event.builder().id(1L).title("Concert Night").build();
        when(eventRepository.findAll()).thenReturn(List.of(event));
        when(bookingRepository.sumTotalAmountByEventIdAndStatus(1L, BookingStatus.CONFIRMED)).thenReturn(new BigDecimal("15000"));
        when(eventSeatRepository.countByEventId(1L)).thenReturn(100L);
        when(eventSeatRepository.countByEventIdAndStatus(1L, SeatStatus.BOOKED)).thenReturn(40L);

        var breakdown = analyticsService.getPerEventBreakdown();

        assertThat(breakdown).hasSize(1);
        var eventStats = breakdown.get(0);
        assertThat(eventStats.getTitle()).isEqualTo("Concert Night");
        assertThat(eventStats.getRevenue()).isEqualByComparingTo("15000");
        assertThat(eventStats.getBookedSeats()).isEqualTo(40L);
        assertThat(eventStats.getOccupancyPercent()).isEqualTo(40.0);
    }

    @Test
    void getBookingsTimeline_usesTheCallerSpecifiedWindow() {
        when(bookingRepository.findDailyConfirmedBookingsSince(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Collections.singletonList(
                        new Object[]{ java.sql.Date.valueOf("2026-01-01"), 3L, new BigDecimal("4500") }
                ));

        var timeline = analyticsService.getBookingsTimeline(7);

        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).getConfirmedBookings()).isEqualTo(3L);
        assertThat(timeline.get(0).getRevenue()).isEqualByComparingTo("4500");
        assertThat(timeline.get(0).getDate()).isEqualTo(java.time.LocalDate.of(2026, 1, 1));
    }
}
