package com.eventbooking.service;

import com.eventbooking.entity.Event;
import com.eventbooking.entity.EventStatus;
import com.eventbooking.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Without this job, an event's status stayed UPCOMING forever after its
 * date/time passed - findByStatus(UPCOMING) is what the public "Browse
 * Events" list and the analytics upcoming-count both query, and nothing
 * anywhere ever moved a past event out of that bucket. That's also why the
 * admin panel looked like it had no history: the only place expired events
 * could show up (GET /events/admin/all) still returns them, but tests
 * exposed the bigger problem this job actually fixes - a past event stayed
 * "UPCOMING" and stayed bookable/visible right alongside real upcoming
 * events, with no automatic signal that it had already happened.
 *
 * Runs every 5 minutes - event expiry isn't as time-sensitive as seat locks
 * or pending-payment cleanup, so this doesn't need minute-level precision.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventExpiryJob {

    private final EventRepository eventRepository;

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    @Transactional
    public void expirePastEvents() {
        LocalDateTime now = LocalDateTime.now();

        List<Event> pastEvents = eventRepository.findByStatusAndEventDateBefore(EventStatus.UPCOMING, now);
        if (pastEvents.isEmpty()) {
            return;
        }

        log.info("Marking {} past event(s) as COMPLETED", pastEvents.size());

        for (Event event : pastEvents) {
            event.setStatus(EventStatus.COMPLETED);
        }

        eventRepository.saveAll(pastEvents);
    }
}
