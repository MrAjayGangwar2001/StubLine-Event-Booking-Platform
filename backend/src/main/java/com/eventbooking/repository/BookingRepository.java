package com.eventbooking.repository;

import com.eventbooking.entity.Booking;
import com.eventbooking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Same N+1 concern as EventSeatRepository: without JOIN FETCH, each
     * booking's seats (and each seat's underlying physical Seat) get lazy
     * loaded individually. A user with 20 past bookings would otherwise
     * trigger dozens of extra queries just to render "My Bookings".
     */
    @Query("SELECT DISTINCT b FROM Booking b " +
           "JOIN FETCH b.bookingSeats bs " +
           "JOIN FETCH bs.eventSeat es " +
           "JOIN FETCH es.seat " +
           "JOIN FETCH b.event " +
           "WHERE b.user.id = :userId " +
           "ORDER BY b.createdAt DESC")
    List<Booking> findByUserIdWithDetails(@Param("userId") Long userId);

    /**
     * Used by PendingBookingCleanupJob to find abandoned checkouts - a
     * PENDING booking whose payment window has long since passed, because
     * the user closed the tab instead of paying or explicitly cancelling.
     */
    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime cutoff);

    List<Booking> findByUserId(Long userId);
    List<Booking> findByEventId(Long eventId);
    List<Booking> findByEventIdAndStatus(Long eventId, BookingStatus status);

    // --- Analytics (Week 5 admin dashboard) ---

    long countByStatus(BookingStatus status);

    long countByEventIdAndStatus(Long eventId, BookingStatus status);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.status = :status")
    java.math.BigDecimal sumTotalAmountByStatus(@Param("status") BookingStatus status);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.event.id = :eventId AND b.status = :status")
    java.math.BigDecimal sumTotalAmountByEventIdAndStatus(@Param("eventId") Long eventId, @Param("status") BookingStatus status);

    /**
     * Bookings-per-day for the timeline chart, revenue-confirmed only.
     * Returns raw Object[] rows (date, count, revenue) rather than a DTO
     * projection interface, since the DATE() truncation is native-SQL-flavored
     * and simplest to just unpack manually in the service layer.
     */
    @Query(value = "SELECT DATE(b.created_at) as day, COUNT(*), COALESCE(SUM(b.total_amount), 0) " +
                    "FROM bookings b WHERE b.status = 'CONFIRMED' AND b.created_at >= :since " +
                    "GROUP BY DATE(b.created_at) ORDER BY day",
            nativeQuery = true)
    List<Object[]> findDailyConfirmedBookingsSince(@Param("since") LocalDateTime since);
}
