package com.eventbooking.service;

import com.eventbooking.dto.announcement.AnnouncementRequest;
import com.eventbooking.dto.announcement.AnnouncementResponse;
import com.eventbooking.entity.Announcement;
import com.eventbooking.exception.ResourceNotFoundException;
import com.eventbooking.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    // Public - no auth required, this is what the homepage banner calls.
    public List<AnnouncementResponse> getActiveAnnouncements() {
        return announcementRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    // Admin-only - includes inactive ones too, so past announcements can be
    // re-activated instead of having to be retyped.
    public List<AnnouncementResponse> getAllAnnouncements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public AnnouncementResponse createAnnouncement(AnnouncementRequest request) {
        Announcement announcement = Announcement.builder()
                .message(request.getMessage())
                .severity(request.getSeverity())
                .active(true)
                .build();
        return toResponse(announcementRepository.save(announcement));
    }

    public AnnouncementResponse setActive(Long id, boolean active) {
        Announcement announcement = findOrThrow(id);
        announcement.setActive(active);
        return toResponse(announcementRepository.save(announcement));
    }

    public void deleteAnnouncement(Long id) {
        Announcement announcement = findOrThrow(id);
        announcementRepository.delete(announcement);
    }

    private Announcement findOrThrow(Long id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found with id: " + id));
    }

    private AnnouncementResponse toResponse(Announcement announcement) {
        return AnnouncementResponse.builder()
                .id(announcement.getId())
                .message(announcement.getMessage())
                .severity(announcement.getSeverity())
                .active(announcement.getActive())
                .createdAt(announcement.getCreatedAt())
                .build();
    }
}
