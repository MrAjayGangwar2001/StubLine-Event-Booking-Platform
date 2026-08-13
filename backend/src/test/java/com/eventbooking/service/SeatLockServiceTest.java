package com.eventbooking.service;

import com.eventbooking.entity.SeatStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests mock Redis directly - they verify SeatLockService's *decision
 * logic* (what it does with Redis's responses), not Redis itself. The
 * integration test in Week 5 exercises this against a real Redis instance
 * with genuinely concurrent threads, which is what actually proves the race
 * condition is fixed.
 */
@ExtendWith(MockitoExtension.class)
class SeatLockServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private SeatLockService seatLockService;

    @BeforeEach
    void setUp() {
        seatLockService = new SeatLockService(redisTemplate, messagingTemplate);
    }

    @Test
    void tryLock_succeeds_whenKeyNotAlreadySet() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("seat_lock:500"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        boolean result = seatLockService.tryLock(10L, 500L, 1L);

        assertThat(result).isTrue();
        verify(messagingTemplate).convertAndSend(eq("/topic/event/10"), any(Object.class));
    }

    @Test
    void tryLock_fails_whenAnotherUserHoldsTheKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("seat_lock:500"), eq("2"), any(Duration.class)))
                .thenReturn(false);
        when(valueOperations.get("seat_lock:500")).thenReturn("1"); // held by user 1, not user 2

        boolean result = seatLockService.tryLock(10L, 500L, 2L);

        assertThat(result).isFalse();
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void tryLock_succeeds_whenCallerAlreadyHoldsTheirOwnLock() {
        // Simulates a double-click or a reconnecting client re-requesting the
        // same seat it already has - should be treated as success, not rejection.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("seat_lock:500"), eq("1"), any(Duration.class)))
                .thenReturn(false);
        when(valueOperations.get("seat_lock:500")).thenReturn("1");

        boolean result = seatLockService.tryLock(10L, 500L, 1L);

        assertThat(result).isTrue();
    }

    @Test
    void isLockedByUser_returnsTrue_onlyForTheActualHolder() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("seat_lock:500")).thenReturn("1");

        assertThat(seatLockService.isLockedByUser(500L, 1L)).isTrue();
        assertThat(seatLockService.isLockedByUser(500L, 2L)).isFalse();
    }

    @Test
    void release_broadcastsAvailable_whenScriptConfirmsDeletion() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("seat_lock:500")), eq("1")))
                .thenReturn(1L);

        boolean released = seatLockService.release(10L, 500L, 1L);

        assertThat(released).isTrue();
        verify(messagingTemplate).convertAndSend(eq("/topic/event/10"), any(Object.class));
    }

    @Test
    void release_returnsFalse_whenCallerDoesNotHoldTheLock() {
        // Lua script returns 0 when the stored value doesn't match the caller's id
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("seat_lock:500")), eq("2")))
                .thenReturn(0L);

        boolean released = seatLockService.release(10L, 500L, 2L);

        assertThat(released).isFalse();
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void extendLock_returnsTrue_whenCallerOwnsTheLock() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("seat_lock:500")), eq("1"), eq("600")))
                .thenReturn(1L);

        boolean extended = seatLockService.extendLock(500L, 1L, Duration.ofMinutes(10));

        assertThat(extended).isTrue();
    }

    @Test
    void extendLock_returnsFalse_whenCallerDoesNotOwnTheLock() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("seat_lock:500")), eq("2"), eq("600")))
                .thenReturn(0L);

        boolean extended = seatLockService.extendLock(500L, 2L, Duration.ofMinutes(10));

        assertThat(extended).isFalse();
    }

    @Test
    void findLockedSeatIds_returnsAllRequestedIds_whenAllAreCurrentlyLocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of("seat_lock:500", "seat_lock:501")))
                .thenReturn(List.of("1", "3"));

        var locked = seatLockService.findLockedSeatIds(List.of(500L, 501L));

        assertThat(locked).containsExactlyInAnyOrder(500L, 501L);
    }

    @Test
    void findLockedSeatIds_treatsNullEntries_asNotLocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        List<String> resultsWithGaps = new java.util.ArrayList<>();
        resultsWithGaps.add("1");
        resultsWithGaps.add(null); // Redis's real behavior for a missing key in MGET
        when(valueOperations.multiGet(List.of("seat_lock:500", "seat_lock:501")))
                .thenReturn(resultsWithGaps);

        var locked = seatLockService.findLockedSeatIds(List.of(500L, 501L));

        assertThat(locked).containsExactly(500L);
    }

    @Test
    void findLockedSeatIds_returnsEmptySet_forEmptyInput() {
        var locked = seatLockService.findLockedSeatIds(List.of());

        assertThat(locked).isEmpty();
        verifyNoInteractions(redisTemplate);
    }
}
