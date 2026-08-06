package com.eventbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents the availability + price of a physical Seat for one specific Event.
 * Kept separate from Seat so the same venue layout can be reused across many events
 * with different pricing/status each time.
 *
 * NOTE: `status` here is the durable, MySQL source of truth (mainly AVAILABLE/BOOKED).
 * The transient LOCKED state during active checkout is tracked in Redis (seat_lock:{id})
 * from Week 3 onward, not written to MySQL on every click - that would defeat the purpose
 * of using Redis for high-frequency lock attempts.
 */
@Entity
@Table(name = "event_seats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id", "seat_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeatStatus status = SeatStatus.AVAILABLE;

    /**
     * Optimistic locking column - doubles as a cheap safety net in case a request
     * ever bypasses the Redis lock path (e.g. an admin correction). The Redis lock
     * remains the primary defense against concurrent double-booking.
     */
    @Version
    private Long version;
}
