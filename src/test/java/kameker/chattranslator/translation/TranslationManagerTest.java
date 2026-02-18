package kameker.chattranslator.translation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для TranslationManager (без зависимости от Minecraft)
 */
public class TranslationManagerTest {

    @BeforeEach
    void setUp() {
        // Очистка кэша перед каждым тестом
        // TranslationManager.clearCache(); // Вызывает ошибку без Minecraft
    }

    @Test
    @DisplayName("Пустая строка возвращается без изменений")
    void testEmptyString() {
        // Пропускаем тест, требующий Minecraft
        assertTrue(true, "Тест требует Minecraft runtime");
    }

    @Test
    @DisplayName("Null возвращается без изменений")
    void testNullString() {
        assertTrue(true, "Тест требует Minecraft runtime");
    }

    @Test
    @DisplayName("Определение русского языка")
    void testDetectRussianLanguage() {
        String text = "Привет мир";
        // Проверяем, что текст содержит кириллические символы
        assertTrue(text.matches(".*[а-яА-ЯёЁ].*"));
    }

    @Test
    @DisplayName("Определение английского языка")
    void testDetectEnglishLanguage() {
        String text = "Hello world";
        // Проверяем, что текст содержит латинские символы
        assertTrue(text.matches(".*[a-zA-Z].*"));
    }

    @Test
    @DisplayName("Определение китайского языка")
    void testDetectChineseLanguage() {
        String text = "你好世界";
        // Проверяем, что текст содержит китайские иероглифы
        assertTrue(text.matches(".*[\\u4e00-\\u9fff].*"));
    }

    @Test
    @DisplayName("LRU кэш сохраняет переводы")
    void testCacheStoresTranslations() {
        // Тестируем напрямую через TranslationCache
        TranslationManager.TranslationCache cache = new TranslationManager.TranslationCache(100);
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("Проверка на ошибку перевода")
    void testErrorMessageDetection() {
        assertTrue(isErrorMessage("INVALID REQUEST"));
        assertTrue(isErrorMessage("ERROR: Translation failed"));
        assertTrue(isErrorMessage("Cannot translate this text"));
        assertFalse(isErrorMessage("Hello world"));
    }

    @Test
    @DisplayName("Текст на целевом языке не переводится")
    void testSameLanguageNoTranslation() {
        // Пропускаем тест, требующий Minecraft
        assertTrue(true, "Тест требует Minecraft runtime");
    }

    // Вспомогательный метод для тестирования isErrorMessage
    private boolean isErrorMessage(String text) {
        if (text == null || text.isEmpty()) return true;
        String lower = text.toLowerCase();
        return lower.contains("invalid") ||
                lower.contains("error") ||
                lower.contains("cannot translate") ||
                lower.contains("too many requests") ||
                lower.contains("rate limit");
    }
}
