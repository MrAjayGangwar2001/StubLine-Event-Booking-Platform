package com.eventbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.UPCOMING;

    // Independent of `status` on purpose: an admin may want to temporarily
    // pause bookings (e.g. venue capacity being re-confirmed) WITHOUT
    // cancelling the event outright. status=CANCELLED still implies
    // bookingEnabled should be false, but the reverse isn't true - a paused
    // UPCOMING event is still a real, upcoming event, just not bookable
    // right now.
    //
    // Nullable is deliberate, NOT an oversight: this column is new, and
    // ddl-auto=update will add it to every EXISTING row as NULL (it can't
    // retroactively apply the @Builder.Default below - that only applies
    // when NEW Java objects are constructed). isBookable() below treats
    // null the same as true for exactly this reason, so events created
    // before this field existed don't silently become unbookable the
    // moment this migration runs.
    private Boolean bookingEnabled;

    // Nullable - only set when status=CANCELLED. Shown to affected users so
    // "why was this cancelled" isn't a support-ticket question.
    @Column(length = 500)
    private String cancellationReason;

    // Nullable on purpose - a poster is optional. Stores a relative URL like
    // "/uploads/posters/12.jpg" (see WebConfig for how that path is served),
    // not the file itself.
    @Column(length = 500)
    private String posterImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Single source of truth for "can someone still lock/book a seat for
     * this event right now" - checked at both seat-lock time
     * (SeatLockController) and booking-creation time (BookingService), so
     * the two can't drift out of sync with each other.
     */
    public boolean isBookable() {
        return status == EventStatus.UPCOMING && !Boolean.FALSE.equals(bookingEnabled);
    }
}
