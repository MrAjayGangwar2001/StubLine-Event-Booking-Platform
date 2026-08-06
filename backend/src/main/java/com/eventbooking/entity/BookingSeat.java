package com.eventbooking.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "booking_seats")
/*
 * NOTE: previously had a unique constraint on event_seat_id, on the
 * assumption a seat could only ever belong to one BookingSeat row, period.
 * That broke as soon as Week 4 introduced a PENDING-payment window: a seat
 * whose first booking attempt was abandoned (payment never completed, or
 * explicitly cancelled) still has a historical BookingSeat row, and a LATER,
 * successful booking for the same physical seat needs its own row too. The
 * constraint that actually matters - "a seat can only be ACTIVELY booked
 * once" - is enforced by EventSeat.status (AVAILABLE/BOOKED) plus the Redis
 * lock, not by this join table, so the DB-level constraint was solving the
 * wrong problem and had to go.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_seat_id", nullable = false)
    private EventSeat eventSeat;
}
