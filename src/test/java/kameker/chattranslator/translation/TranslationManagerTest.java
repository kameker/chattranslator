package kameker.chattranslator.translation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TranslationManagerTest {

    @Test
    void requestKeepsSourceAndTargetInNamedFields() {
        var request = new TranslationManager.TranslationRequest("hello", "en", "ru");
        assertEquals("en", request.sourceLanguage());
        assertEquals("ru", request.targetLanguage());
        assertThrows(IllegalArgumentException.class,
                () -> new TranslationManager.TranslationRequest("hello", "en", " "));
    }

    @Test
    void detectsSupportedWritingSystems() {
        assertEquals("ru", TranslationEngine.detectLanguage("Привет мир"));
        assertEquals("uk", TranslationEngine.detectLanguage("Привіт, як твої справи?"));
        assertEquals("en", TranslationEngine.detectLanguage("Hello world"));
        assertEquals("zh", TranslationEngine.detectLanguage("你好世界"));
        assertEquals("ja", TranslationEngine.detectLanguage("こんにちは世界"));
        assertEquals("ko", TranslationEngine.detectLanguage("안녕하세요"));
        assertEquals("es", TranslationEngine.detectLanguage("¿Cómo está?"));
    }

    @Test
    void detectsProviderErrorMessages() {
        assertTrue(TranslationEngine.isErrorMessage("INVALID REQUEST"));
        assertTrue(TranslationEngine.isErrorMessage("ERROR: Translation failed"));
        assertTrue(TranslationEngine.isErrorMessage("Too many requests"));
        assertFalse(TranslationEngine.isErrorMessage("C++ is useful"));
    }

    @Test
    void fallbackStartsWithPreferredProvider() {
        List<String> calls = new java.util.ArrayList<>();
        Map<String, TranslationEngine.Service> services = new LinkedHashMap<>();
        services.put("first", (text, source, target) -> {
            calls.add("first");
            return null;
        });
        services.put("preferred", (text, source, target) -> {
            calls.add("preferred");
            assertEquals("en", source);
            assertEquals("ru", target);
            return "перевод";
        });

        String result = TranslationEngine.translateWithFallback(
                "text", "en", "ru", "preferred", services,
                List.of("first", "preferred"), ignored -> true);

        assertEquals("перевод", result);
        assertEquals(List.of("preferred"), calls);
    }

    @Test
    void fallbackContinuesAfterFailureAndErrorResponse() {
        List<String> calls = new java.util.ArrayList<>();
        Map<String, TranslationEngine.Service> services = new LinkedHashMap<>();
        services.put("broken", (text, source, target) -> {
            calls.add("broken");
            throw new java.io.IOException("offline");
        });
        services.put("bad-response", (text, source, target) -> {
            calls.add("bad-response");
            return "ERROR: unavailable";
        });
        services.put("working", (text, source, target) -> {
            calls.add("working");
            return "ok";
        });

        String result = TranslationEngine.translateWithFallback(
                "text", "en", "ru", "broken", services,
                List.of("bad-response", "working"), ignored -> true);

        assertEquals("ok", result);
        assertEquals(List.of("broken", "bad-response", "working"), calls);
    }

    @Test
    void fallbackHonoursRateLimiter() {
        Map<String, TranslationEngine.Service> services = Map.of(
                "blocked", (text, source, target) -> fail("Blocked provider must not be called"),
                "working", (text, source, target) -> "ok"
        );

        String result = TranslationEngine.translateWithFallback(
                "text", "en", "ru", "blocked", services,
                List.of("working"), service -> !service.equals("blocked"));

        assertEquals("ok", result);
    }

    @Test
    void splitsLongUnpunctuatedTextWithoutOversizedChunks() {
        String text = "word ".repeat(2_000).trim();
        List<String> chunks = TranslationEngine.splitText(text, 4_000);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 4_000));
        assertEquals(text, String.join(" ", chunks));
    }
}
