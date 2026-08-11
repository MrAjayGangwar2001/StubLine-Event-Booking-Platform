package com.eventbooking.service;

import com.eventbooking.dto.event.SeatGenerationRequest;
import com.eventbooking.dto.event.SeatResponse;
import com.eventbooking.entity.Seat;
import com.eventbooking.entity.SeatTier;
import com.eventbooking.entity.Venue;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final VenueService venueService;

    /**
     * Generates the physical seat layout for a venue once (e.g. rows A-J).
     * This layout is reused across every event held at that venue -
     * only price/status (EventSeat) differ per event.
     */
    public List<SeatResponse> generateSeatsForVenue(Long venueId, SeatGenerationRequest request) {
        Venue venue = venueService.findVenueOrThrow(venueId);

        if (!seatRepository.findByVenueId(venueId).isEmpty()) {
            // Without this check, a second call hits the (venue_id, row_label,
            // seat_number) unique constraint and surfaces as a raw 500 instead
            // of a clear, actionable message.
            throw new BadRequestException("Seats have already been generated for this venue");
        }

        List<Seat> seats = new ArrayList<>();

        // Without this check, the venue's totalCapacity is pure decoration -
        // rows can add up to any number, more or less than what was declared
        // at venue-creation time, and nothing ever catches the mismatch.
        int requestedSeatCount = request.getRows().stream()
                .mapToInt(SeatGenerationRequest.RowConfig::getSeatCount)
                .sum();
        // if (requestedSeatCount != venue.getTotalCapacity()) {   // this is for Exact seat Division as per Allocation
        if (requestedSeatCount > venue.getTotalCapacity()) {
            throw new BadRequestException(
                    "Seat layout totals " + requestedSeatCount + " seats, which is more than this venue's capacity of "
                            + venue.getTotalCapacity()
                            + ". Reduce a row's seat count or increase the venue's capacity.");
        }

        for (SeatGenerationRequest.RowConfig row : request.getRows()) {
            SeatTier tier = SeatTier.valueOf(row.getTier().toUpperCase());
            for (int seatNum = 1; seatNum <= row.getSeatCount(); seatNum++) {
                seats.add(Seat.builder()
                        .venue(venue)
                        .rowLabel(row.getRowLabel().toUpperCase())
                        .seatNumber(seatNum)
                        .tier(tier)
                        .build());
            }
        }

        return seatRepository.saveAll(seats).stream().map(this::toResponse).toList();
    }

    public List<SeatResponse> getSeatsForVenue(Long venueId) {
        return seatRepository.findByVenueId(venueId).stream().map(this::toResponse).toList();
    }

    /**
     * Package-private raw entities, used internally by EventSeatService when
     * generating per-event pricing - avoids re-fetching or extra DTO mapping.
     */
    List<Seat> getRawSeatsForVenue(Long venueId) {
        return seatRepository.findByVenueId(venueId);
    }

    private SeatResponse toResponse(Seat seat) {
        return SeatResponse.builder()
                .id(seat.getId())
                .rowLabel(seat.getRowLabel())
                .seatNumber(seat.getSeatNumber())
                .tier(seat.getTier())
                .build();
    }
}
