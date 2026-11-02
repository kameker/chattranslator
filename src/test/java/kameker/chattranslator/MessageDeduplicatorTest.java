package kameker.chattranslator;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MessageDeduplicatorTest {
    @Test
    void rejectsDuplicateUntilTimeoutExpires() {
        MessageDeduplicator deduplicator = new MessageDeduplicator();
        assertTrue(deduplicator.markIfNew("message", 1_000, 3_000));
        assertFalse(deduplicator.markIfNew("message", 2_000, 3_000));
        assertTrue(deduplicator.markIfNew("message", 4_001, 3_000));
    }

    @Test
    void permitsDifferentMessagesAndCleansExpiredEntries() {
        MessageDeduplicator deduplicator = new MessageDeduplicator();
        assertTrue(deduplicator.markIfNew("one", 1_000, 100));
        assertTrue(deduplicator.markIfNew("two", 1_000, 100));
        deduplicator.clean(1_101, 100);
        assertEquals(0, deduplicator.size());
    }

    @Test
    void admitsOnlyOneConcurrentCopy() throws InterruptedException {
        MessageDeduplicator deduplicator = new MessageDeduplicator();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    if (deduplicator.markIfNew("same", 1_000, 3_000)) admitted.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        assertEquals(1, admitted.get());
    }
}
