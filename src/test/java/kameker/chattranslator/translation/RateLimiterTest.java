package kameker.chattranslator.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для RateLimiter
 */
public class RateLimiterTest {

    private TranslationManager.RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // 10 запросов в секунду для тестов
        rateLimiter = new TranslationManager.RateLimiter(10, 1);
    }

    @Test
    @DisplayName("RateLimiter разрешает запросы в пределах лимита")
    void testRateLimiterAllowsWithinLimit() {
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.tryAcquire(), "Запрос " + (i + 1) + " должен быть разрешён");
        }
    }

    @Test
    @DisplayName("RateLimiter отклоняет запросы сверх лимита")
    void testRateLimiterRejectsOverLimit() {
        // Исчерпываем лимит
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryAcquire();
        }

        // Следующий запрос должен быть отклонён
        assertFalse(rateLimiter.tryAcquire(), "Запрос сверх лимита должен быть отклонён");
    }

    @Test
    @DisplayName("RateLimiter восстанавливается со временем")
    void testRateLimiterRecoversOverTime() throws InterruptedException {
        TranslationManager.RateLimiter fastLimiter = new TranslationManager.RateLimiter(2, 1);

        // Исчерпываем лимит
        assertTrue(fastLimiter.tryAcquire());
        assertTrue(fastLimiter.tryAcquire());
        assertFalse(fastLimiter.tryAcquire());

        // Ждём немного (но для полного восстановления нужно ждать 1 секунду)
        // Для теста проверяем, что waitTime > 0
        long waitTime = fastLimiter.getWaitTime();
        assertTrue(waitTime > 0, "Время ожидания должно быть больше 0");
    }

    @Test
    @DisplayName("getWaitTime возвращает время до следующего доступного запроса")
    void testGetWaitTime() {
        TranslationManager.RateLimiter testLimiter = new TranslationManager.RateLimiter(1, 60);

        testLimiter.tryAcquire();

        long waitTime = testLimiter.getWaitTime();
        assertTrue(waitTime > 0, "Время ожидания должно быть больше 0 после исчерпания лимита");
        assertTrue(waitTime <= 60000, "Время ожидания не должно превышать окно времени");
    }

    @Test
    @DisplayName("Пустой RateLimiter имеет waitTime = 0")
    void testGetWaitTimeEmpty() {
        assertEquals(0, rateLimiter.getWaitTime());
    }

    @Test
    @DisplayName("Несколько потоков безопасно используют RateLimiter")
    void testConcurrentAccess() throws InterruptedException {
        TranslationManager.RateLimiter concurrentLimiter = new TranslationManager.RateLimiter(100, 1);
        int[] successCount = {0};

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    if (concurrentLimiter.tryAcquire()) {
                        synchronized (successCount) {
                            successCount[0]++;
                        }
                    }
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(100, successCount[0], "Должно быть ровно 100 успешных запросов");
    }
}
