package kameker.chattranslator.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для LRU кэша переводов
 */
public class TranslationCacheTest {

    private TranslationManager.TranslationCache cache;

    @BeforeEach
    void setUp() {
        cache = new TranslationManager.TranslationCache(100);
    }

    @Test
    @DisplayName("Кэш сохраняет и возвращает значения")
    void testCachePutAndGet() {
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
    }

    @Test
    @DisplayName("Кэш возвращает null для несуществующих ключей")
    void testCacheGetNonExistent() {
        assertNull(cache.get("nonexistent"));
    }

    @Test
    @DisplayName("LRU кэш удаляет старые записи при переполнении")
    void testLRUEviction() {
        TranslationManager.TranslationCache smallCache = new TranslationManager.TranslationCache(3);

        smallCache.put("key1", "value1");
        smallCache.put("key2", "value2");
        smallCache.put("key3", "value3");

        // Добавляем 4-й элемент, должен удалиться самый старый (key1)
        smallCache.put("key4", "value4");

        assertNull(smallCache.get("key1"), "Самый старый ключ должен быть удалён");
        assertEquals("value2", smallCache.get("key2"));
        assertEquals("value3", smallCache.get("key3"));
        assertEquals("value4", smallCache.get("key4"));
    }

    @Test
    @DisplayName("LRU кэш обновляет порядок доступа при чтении")
    void testLRUAccessOrder() {
        TranslationManager.TranslationCache smallCache = new TranslationManager.TranslationCache(3);

        smallCache.put("key1", "value1");
        smallCache.put("key2", "value2");
        smallCache.put("key3", "value3");

        // Обращаемся к key1, делая его недавно использованным
        smallCache.get("key1");

        // Добавляем новый ключ, должен удалиться key2 (самый старый из оставшихся)
        smallCache.put("key4", "value4");

        assertEquals("value1", smallCache.get("key1"), "key1 должен сохраниться");
        assertNull(smallCache.get("key2"), "key2 должен быть удалён");
    }

    @Test
    @DisplayName("Очистка кэша")
    void testCacheClear() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        cache.clear();

        assertEquals(0, cache.size());
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
    }

    @Test
    @DisplayName("Подсчёт hit rate")
    void testHitRateCalculation() {
        cache.put("key1", "value1");

        cache.get("key1"); // hit
        cache.get("key1"); // hit
        cache.get("key2"); // miss
        cache.get("key3"); // miss

        double hitRate = cache.getHitRate();
        assertEquals(50.0, hitRate, 0.01);
    }

    @Test
    @DisplayName("Hit rate 0% при всех промахах")
    void testZeroHitRate() {
        cache.get("miss1");
        cache.get("miss2");
        cache.get("miss3");

        assertEquals(0.0, cache.getHitRate(), 0.01);
    }

    @Test
    @DisplayName("Hit rate 100% при всех попаданиях")
    void testHundredHitRate() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        cache.get("key1");
        cache.get("key2");

        assertEquals(100.0, cache.getHitRate(), 0.01);
    }

    @Test
    @DisplayName("Размер кэша ограничивается maxSize")
    void testCacheSizeLimit() {
        TranslationManager.TranslationCache smallCache = new TranslationManager.TranslationCache(5);

        for (int i = 0; i < 100; i++) {
            smallCache.put("key" + i, "value" + i);
        }

        assertTrue(smallCache.size() <= 5, "Размер кэша не должен превышать максимум");
    }
}
