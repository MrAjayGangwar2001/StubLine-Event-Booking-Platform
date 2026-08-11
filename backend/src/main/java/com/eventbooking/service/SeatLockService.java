package com.eventbooking.service;

import com.eventbooking.dto.ws.SeatStatusUpdate;
import com.eventbooking.entity.SeatStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The centerpiece of Week 3: prevents two users from booking the same seat by
 * making them race for a Redis key instead of a database row.
 *
 * Why Redis SETNX over the DB's @Version column (Week 2's approach):
 *  - It's an in-memory, single round-trip check - the loser is rejected in
 *    milliseconds, before the request ever touches MySQL, instead of finding
 *    out only after a full DB round-trip and an OptimisticLockingFailureException.
 *  - It naturally expresses "hold this for 5 minutes" via TTL, which is a
 *    checkout window, not just a write-conflict guard.
 *  - It coordinates correctly across multiple app server instances, since
 *    they all share the same Redis instance - a per-JVM lock (e.g. `synchronized`)
 *    would not.
 *
 * Lock key: "seat_lock:{eventSeatId}" -> value "{userId}", with a TTL.
 * SETNX (via Redis's `setIfAbsent`) is atomic: if two requests race, only one
 * SET succeeds, and that's the winner. No read-then-write gap exists here,
 * unlike the Week 2 optimistic-locking flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeatLockService {

    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "seat_lock:";

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    // Lua script: only delete the key if the value still matches the caller's
    // userId. Without this, User A's lock could be released by User B's
    // cleanup call (or a stale retry) if they raced on the same key after
    // expiry. Compare-and-delete has to be atomic, so this can't be a plain
    // GET followed by a DEL from application code - another request could
    // slip in between those two calls.
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end",
            Long.class
    );

    // Same compare-then-act shape as RELEASE_SCRIPT, but extends the TTL
    // instead of deleting the key - still needs to be atomic so a lock can't
    // change hands in the gap between checking ownership and updating it.
    private static final DefaultRedisScript<Long> EXTEND_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('expire', KEYS[1], ARGV[2]) " +
            "else " +
            "  return 0 " +
            "end",
            Long.class
    );

    /**
     * Attempts to acquire the lock for a seat on behalf of a user.
     * Returns true if acquired (or already held by this same user), false if
     * someone else currently holds it.
     */
    public boolean tryLock(Long eventId, Long eventSeatId, Long userId) {
        String key = lockKey(eventSeatId);
        String userIdStr = userId.toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, userIdStr, LOCK_TTL);

        if (Boolean.TRUE.equals(acquired)) {
            broadcast(eventId, eventSeatId, SeatStatus.LOCKED, (int) LOCK_TTL.getSeconds());
            return true;
        }

        // Not acquired - but if WE already hold it (e.g. user double-clicked,
        // or their seat-map reconnected), treat it as a successful "renewal"
        // rather than a rejection.
        String currentHolder = redisTemplate.opsForValue().get(key);
        return userIdStr.equals(currentHolder);
    }

    /**
     * Releases the lock, but only if the given user is the one who holds it.
     * Called both explicitly (user deselects a seat, or completes booking)
     * and is otherwise left to expire naturally via TTL if the user abandons
     * checkout entirely.
     */
    public boolean release(Long eventId, Long eventSeatId, Long userId) {
        String key = lockKey(eventSeatId);
        Long result = redisTemplate.execute(RELEASE_SCRIPT, List.of(key), userId.toString());
        boolean released = result != null && result == 1L;

        if (released) {
            broadcast(eventId, eventSeatId, SeatStatus.AVAILABLE, null);
        }
        return released;
    }

    /**
     * Called right before booking is finalized, to confirm the caller still
     * holds the lock they think they hold (it could have expired mid-checkout,
     * e.g. if payment took longer than the TTL).
     */
    public boolean isLockedByUser(Long eventSeatId, Long userId) {
        String currentHolder = redisTemplate.opsForValue().get(lockKey(eventSeatId));
        return userId.toString().equals(currentHolder);
    }

    /**
     * Whether ANYONE currently holds this lock, regardless of who. Used by
     * EventSeatService's seat-map read so a client loading the page fresh
     * (not already connected when the lock was acquired) still sees the seat
     * as unavailable, instead of only finding out via a WebSocket message
     * they were never around to receive.
     */
    public boolean isLocked(Long eventSeatId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(eventSeatId)));
    }

    /**
     * Batch version of isLocked() for rendering a whole seat map at once -
     * one Redis round trip (MGET) instead of one per seat. For a 200-seat
     * venue that's the difference between 1 call and 200 on every page load,
     * which matters even though Redis round trips are individually cheap.
     */
    public Set<Long> findLockedSeatIds(List<Long> eventSeatIds) {
        if (eventSeatIds.isEmpty()) {
            return Set.of();
        }
        List<String> keys = eventSeatIds.stream().map(this::lockKey).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        Set<Long> locked = new HashSet<>();
        if (values == null) {
            return locked;
        }
        for (int i = 0; i < eventSeatIds.size(); i++) {
            if (values.get(i) != null) {
                locked.add(eventSeatIds.get(i));
            }
        }
        return locked;
    }

    /**
     * Re-issues the TTL on a lock the caller already owns, without releasing
     * and re-acquiring it (which would create a tiny window for someone else
     * to grab it in between). Used once a booking moves into the payment
     * step, so the checkout window can be longer than the default 5-minute
     * seat-selection hold without weakening the atomicity of "only the
     * current holder can touch this key".
     */
    public boolean extendLock(Long eventSeatId, Long userId, Duration newTtl) {
        Long result = redisTemplate.execute(EXTEND_SCRIPT, List.of(lockKey(eventSeatId)),
                userId.toString(), String.valueOf(newTtl.getSeconds()));
        return result != null && result == 1L;
    }

    /**
     * Called once a booking is confirmed - the seat is now durably BOOKED in
     * MySQL, so the temporary Redis lock has served its purpose and can be
     * dropped immediately rather than waiting out its TTL.
     */
    public void clearLockAfterBooking(Long eventId, Long eventSeatId, Long userId) {
        String key = lockKey(eventSeatId);
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key), userId.toString());
        broadcast(eventId, eventSeatId, SeatStatus.BOOKED, null);
    }

    public void broadcastAvailable(Long eventId, Long eventSeatId) {
        broadcast(eventId, eventSeatId, SeatStatus.AVAILABLE, null);
    }

    private void broadcast(Long eventId, Long eventSeatId, SeatStatus status, Integer ttlSeconds) {
        SeatStatusUpdate update = SeatStatusUpdate.builder()
                .eventSeatId(eventSeatId)
                .status(status)
                .lockTtlSeconds(ttlSeconds)
                .build();
        messagingTemplate.convertAndSend("/topic/event/" + eventId, update);
    }

    private String lockKey(Long eventSeatId) {
        return KEY_PREFIX + eventSeatId;
    }
}
