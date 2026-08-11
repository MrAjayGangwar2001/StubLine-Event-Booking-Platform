package com.eventbooking.service;

import com.eventbooking.dto.event.EventSeatGenerationRequest;
import com.eventbooking.dto.event.SeatMapResponse;
import com.eventbooking.entity.*;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.repository.EventSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventSeatService {

    private final EventSeatRepository eventSeatRepository;
    private final EventService eventService;
    private final SeatService seatService;
    private final SeatLockService seatLockService;

    /**
     * Called once by an admin after creating an event, to put its seats "on sale".
     * Creates one EventSeat per physical Seat in the event's venue, priced by tier.
     */
    public List<SeatMapResponse> generateEventSeats(Long eventId, EventSeatGenerationRequest request) {
        Event event = eventService.findEventOrThrow(eventId);

        if (!eventSeatRepository.findByEventId(eventId).isEmpty()) {
            throw new BadRequestException("Seats have already been generated for this event");
        }

        List<Seat> venueSeats = seatService.getRawSeatsForVenue(event.getVenue().getId());
        if (venueSeats.isEmpty()) {
            throw new BadRequestException("Venue has no seats defined yet - generate the venue layout first");
        }

        List<EventSeat> eventSeats = venueSeats.stream()
                .map(seat -> {
                    BigDecimal price = request.getTierPrices().get(seat.getTier().name());
                    if (price == null) {
                        throw new BadRequestException("Missing price for tier: " + seat.getTier());
                    }
                    return EventSeat.builder()
                            .event(event)
                            .seat(seat)
                            .price(price)
                            .status(SeatStatus.AVAILABLE)
                            .build();
                })
                .toList();

        eventSeatRepository.saveAll(eventSeats);
        return getSeatMap(eventId);
    }

    /**
     * The DB's EventSeat.status only ever holds AVAILABLE or BOOKED - LOCKED
     * is intentionally never written there (it's a transient, fast-changing
     * checkout hold, not something worth a DB write every time someone clicks
     * a seat). That means a plain DB read alone can't tell a fresh page load
     * "this seat is mid-checkout right now" - only clients already connected
     * via WebSocket *when the lock was acquired* would know that. So this
     * cross-checks Redis for any seat the DB reports as AVAILABLE, and
     * reports it as LOCKED instead if someone currently holds it. Without
     * this, a user loading the seat map for the first time would see a
     * seat as selectable right up until they click it and get rejected.
     */
    public List<SeatMapResponse> getSeatMap(Long eventId) {
        List<EventSeat> eventSeats = eventSeatRepository.findByEventIdWithSeat(eventId);

        List<Long> availableSeatIds = eventSeats.stream()
                .filter(es -> es.getStatus() == SeatStatus.AVAILABLE)
                .map(EventSeat::getId)
                .toList();
        Set<Long> lockedSeatIds = seatLockService.findLockedSeatIds(availableSeatIds);

        return eventSeats.stream()
                .map(es -> {
                    SeatStatus effectiveStatus = lockedSeatIds.contains(es.getId()) ? SeatStatus.LOCKED : es.getStatus();
                    return SeatMapResponse.builder()
                            .eventSeatId(es.getId())
                            .rowLabel(es.getSeat().getRowLabel())
                            .seatNumber(es.getSeat().getSeatNumber())
                            .tier(es.getSeat().getTier())
                            .price(es.getPrice())
                            .status(effectiveStatus)
                            .build();
                })
                .sorted((a, b) -> {
                    int rowCmp = a.getRowLabel().compareTo(b.getRowLabel());
                    return rowCmp != 0 ? rowCmp : a.getSeatNumber().compareTo(b.getSeatNumber());
                })
                .toList();
    }
}
