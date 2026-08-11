package com.eventbooking.service;

import com.eventbooking.dto.event.VenueRequest;
import com.eventbooking.dto.event.VenueResponse;
import com.eventbooking.entity.Venue;
import com.eventbooking.exception.ResourceNotFoundException;
import com.eventbooking.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles venue CRUD only. Physical seat layout generation lives in
 * SeatService (POST /api/venues/{id}/seats/generate) as a deliberate
 * separate step, so a venue can exist before its seat map is defined.
 */
@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueResponse createVenue(VenueRequest request) {
        Venue venue = Venue.builder()
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .totalCapacity(request.getTotalCapacity())
                .build();

        return toResponse(venueRepository.save(venue));
    }

    public List<VenueResponse> getAllVenues() {
        return venueRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public VenueResponse getVenueById(Long id) {
        return toResponse(findVenueOrThrow(id));
    }

    public Venue findVenueOrThrow(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + id));
    }

    private VenueResponse toResponse(Venue venue) {
        return VenueResponse.builder()
                .id(venue.getId())
                .name(venue.getName())
                .address(venue.getAddress())
                .city(venue.getCity())
                .totalCapacity(venue.getTotalCapacity())
                .build();
    }
}
