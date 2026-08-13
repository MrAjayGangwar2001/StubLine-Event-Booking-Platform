package com.eventbooking.integration;

import com.eventbooking.service.SeatLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE flagship concurrency test for this project: fires 50 threads at the
 * exact same seat lock, all at once, against a real Redis instance (not a
 * mock), and proves exactly one of them wins.
 *
 * Why this test is worth more than the unit tests: SeatLockServiceTest mocks
 * StringRedisTemplate and verifies SeatLockService *calls* setIfAbsent with
 * the right arguments - it can't prove two real threads can't both "win" a
 * race, because a mock has no actual race conditions to get wrong. This test
 * removes that gap entirely by using a genuine Redis instance (via
 * Testcontainers) and genuine OS threads, released simultaneously via a
 * CountDownLatch gate so they contend for the SETNX as close to
 * simultaneously as the JVM/OS scheduler allows.
 */
class SeatLockConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private SeatLockService seatLockService;

    @Test
    void fiftyThreads_raceForTheSameSeat_onlyOneAcquiresTheLock() throws InterruptedException {
        final long eventId = 1L;
        final long eventSeatId = 999_001L; // arbitrary - SeatLockService operates purely on this id as a Redis key, no DB row needed
        final int threadCount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch allThreadsReady = new CountDownLatch(threadCount);
        CountDownLatch releaseGate = new CountDownLatch(1);
        CountDownLatch allThreadsDone = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Long> winningUserIds = Collections.synchronizedList(new java.util.ArrayList<>());

        for (long userId = 1; userId <= threadCount; userId++) {
            final long thisUserId = userId;
            executor.submit(() -> {
                try {
                    allThreadsReady.countDown();
                    releaseGate.await(); // every thread blocks here until ALL 50 are ready

                    boolean acquired = seatLockService.tryLock(eventId, eventSeatId, thisUserId);
                    if (acquired) {
                        successCount.incrementAndGet();
                        winningUserIds.add(thisUserId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allThreadsDone.countDown();
                }
            });
        }

        allThreadsReady.await(5, TimeUnit.SECONDS); // wait for all 50 to reach the gate
        releaseGate.countDown();                    // release them all at the same instant
        boolean completed = allThreadsDone.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("all 50 threads should finish within the timeout").isTrue();
        assertThat(successCount.get())
                .as("exactly one of 50 concurrent lock attempts on the same seat should succeed")
                .isEqualTo(1);
        assertThat(winningUserIds).hasSize(1);
        assertThat(seatLockService.isLockedByUser(eventSeatId, winningUserIds.get(0)))
                .as("the seat should be held by whichever user actually won the race")
                .isTrue();
    }

    @Test
    void afterWinnerReleases_anotherThreadCanAcquireTheSameSeat() throws InterruptedException {
        final long eventId = 2L;
        final long eventSeatId = 999_002L;

        boolean firstAcquired = seatLockService.tryLock(eventId, eventSeatId, 1L);
        assertThat(firstAcquired).isTrue();

        boolean secondAttemptWhileHeld = seatLockService.tryLock(eventId, eventSeatId, 2L);
        assertThat(secondAttemptWhileHeld).isFalse();

        boolean released = seatLockService.release(eventId, eventSeatId, 1L);
        assertThat(released).isTrue();

        boolean secondAttemptAfterRelease = seatLockService.tryLock(eventId, eventSeatId, 2L);
        assertThat(secondAttemptAfterRelease)
                .as("once released, a different user should be able to acquire the same seat")
                .isTrue();
    }
}
