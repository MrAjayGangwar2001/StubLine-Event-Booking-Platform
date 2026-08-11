package com.eventbooking.repository;

import com.eventbooking.entity.EventSeat;
import com.eventbooking.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    /**
     * Plain findByEventId would lazy-load each EventSeat's `seat` association
     * one row at a time (N+1: 1 query for the list + N queries for each seat).
     * For a 200-seat venue that's 201 queries on every seat-map page load.
     * JOIN FETCH pulls the physical Seat in the same query instead.
     */
    @Query("SELECT es FROM EventSeat es JOIN FETCH es.seat WHERE es.event.id = :eventId")
    List<EventSeat> findByEventIdWithSeat(@Param("eventId") Long eventId);

    /**
     * Plain findAllById() would return EventSeat objects whose `seat` and
     * `event` lazy associations are only safely accessible while the
     * repository call's own short-lived transaction is still open. Since
     * BookingService.createBooking() deliberately does NOT wrap this call in
     * a transaction (to avoid holding a DB connection open across the
     * external Razorpay call that follows), any lazy access afterward would
     * throw LazyInitializationException. JOIN FETCH sidesteps that by
     * loading both associations eagerly as part of this one query.
     */
    @Query("SELECT es FROM EventSeat es JOIN FETCH es.seat JOIN FETCH es.event WHERE es.id IN :ids")
    List<EventSeat> findAllByIdWithSeatAndEvent(@Param("ids") List<Long> ids);

    List<EventSeat> findByEventId(Long eventId);
    List<EventSeat> findByEventIdAndStatus(Long eventId, SeatStatus status);

    // --- Analytics (Week 5 admin dashboard) ---
    long countByEventId(Long eventId);
    long countByEventIdAndStatus(Long eventId, SeatStatus status);
    long countByStatus(SeatStatus status);
}
