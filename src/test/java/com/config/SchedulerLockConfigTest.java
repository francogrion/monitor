package com.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(SchedulerLockConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SchedulerLockConfigTest {

    private static final String LOCK_NAME = "test-lock";

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Expire rather than delete: ShedLock caches that the row exists and afterwards only UPDATEs it
    @AfterEach
    void expireLock() {
        jdbcTemplate.update("UPDATE shedlock SET lock_until = TIMESTAMP '2000-01-01 00:00:00' WHERE name = ?", LOCK_NAME);
    }

    @Test
    void shouldGrantLockWhenFree() {
        Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration());

        assertTrue(lock.isPresent());
        lock.get().unlock();
    }

    @Test
    void shouldRejectSecondAcquisitionWhileLockIsHeld() {
        Optional<SimpleLock> first = lockProvider.lock(lockConfiguration());
        Optional<SimpleLock> second = lockProvider.lock(lockConfiguration());

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
        first.get().unlock();
    }

    @Test
    void shouldGrantLockAgainAfterRelease() {
        lockProvider.lock(lockConfiguration()).orElseThrow().unlock();

        Optional<SimpleLock> again = lockProvider.lock(lockConfiguration());

        assertTrue(again.isPresent());
        again.get().unlock();
    }

    @Test
    void shouldGrantLockToExactlyOneOfTwoConcurrentContenders() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Optional<SimpleLock>> a = executor.submit(() -> {
                start.await();
                return lockProvider.lock(lockConfiguration());
            });
            Future<Optional<SimpleLock>> b = executor.submit(() -> {
                start.await();
                return lockProvider.lock(lockConfiguration());
            });
            start.countDown();

            Optional<SimpleLock> lockA = a.get();
            Optional<SimpleLock> lockB = b.get();

            assertEquals(1, (lockA.isPresent() ? 1 : 0) + (lockB.isPresent() ? 1 : 0));
            lockA.ifPresent(SimpleLock::unlock);
            lockB.ifPresent(SimpleLock::unlock);
        } finally {
            executor.shutdownNow();
        }
    }

    private static LockConfiguration lockConfiguration() {
        return new LockConfiguration(Instant.now(), LOCK_NAME, Duration.ofMinutes(1), Duration.ZERO);
    }
}
