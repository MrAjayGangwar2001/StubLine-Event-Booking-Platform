package com.eventbooking.repository;

import com.eventbooking.entity.Event;
import com.eventbooking.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStatus(EventStatus status);
    List<Event> findByCategoryIgnoreCase(String category);
    long countByStatus(EventStatus status);

    // Admin "All Events" / history view - every event regardless of status,
    // most recent first, so completed/expired events are visible instead of
    // silently disappearing once getAllUpcomingEvents() filters them out.
    List<Event> findAllByOrderByEventDateDesc();

    // Used by the expiry job: events still marked UPCOMING whose date/time
    // has already passed, so they can be flipped to COMPLETED.
    List<Event> findByStatusAndEventDateBefore(EventStatus status, LocalDateTime cutoff);
}
