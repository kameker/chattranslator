package kameker.chattranslator.translation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kameker.chattranslator.config.ModConfig;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranslationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationManager.class);
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    // Кэш переводов
    private static final Map<String, String> TRANSLATION_CACHE = new ConcurrentHashMap<>();

    // Поддерживаемые языки и их паттерны для автоопределения
    private static final Map<String, Pattern> LANGUAGE_PATTERNS = Map.of(
            "ru", Pattern.compile("[а-яА-ЯёЁ]"),
            "en", Pattern.compile("[a-zA-Z]"),
            "da", Pattern.compile("[æøåÆØÅ]"),
            "es", Pattern.compile("[áéíóúñü¿¡]"),
            "fr", Pattern.compile("[àâæçéèêëîïôœùûüÿ]"),
            "de", Pattern.compile("[äöüß]"),
            "zh", Pattern.compile("[\\u4e00-\\u9fff]"),
            "ja", Pattern.compile("[\\u3040-\\u30ff\\u4e00-\\u9fff]"),
            "ko", Pattern.compile("[\\uac00-\\ud7af\\u1100-\\u11ff]")
    );

    public static CompletableFuture<String> translate(String text) {
        return translate(text,
                ModConfig.TARGET_LANGUAGE.get(),
                ModConfig.AUTO_DETECT_SOURCE.get() ? null : ModConfig.SOURCE_LANGUAGE.get());
    }

    public static CompletableFuture<String> translate(String text, String targetLang, String sourceLang) {
        final String finalText = text;
        final String finalTargetLang = targetLang;
        final String finalSourceLang = sourceLang;

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (finalText == null || finalText.trim().isEmpty()) {
                    return finalText;
                }

                // Проверяем кэш
                String cacheKey = finalText + "|" + finalTargetLang + "|" + finalSourceLang;
                if (TRANSLATION_CACHE.containsKey(cacheKey)) {
                    LOGGER.debug("Cache hit: {}", cacheKey);
                    return TRANSLATION_CACHE.get(cacheKey);
                }

                // Определяем исходный язык, если нужно
                String detectedSource = finalSourceLang;
                if (detectedSource == null) {
                    detectedSource = detectLanguage(finalText);
                    LOGGER.debug("Detected source language: {}", detectedSource);
                }

                // Если текст уже на целевом языке, не переводим
                if (detectedSource.equals(finalTargetLang)) {
                    LOGGER.debug("Text already in target language");
                    return finalText;
                }

                // Пробуем MyMemory API
                String translated = translateWithMyMemory(finalText, detectedSource, finalTargetLang);

                if (translated != null && !translated.equals(finalText) && !isErrorMessage(translated)) {
                    TRANSLATION_CACHE.put(cacheKey, translated);
                    LOGGER.info("Translated [{}->{}]: {} -> {}",
                            detectedSource, finalTargetLang, finalText, translated);
                    return translated;
                }

                // Если MyMemory не сработал, пробуем другие API
                translated = translateWithLingva(finalText, finalTargetLang);
                if (translated != null && !translated.equals(finalText)) {
                    TRANSLATION_CACHE.put(cacheKey, translated);
                    return translated;
                }

                return finalText;

            } catch (Exception e) {
                LOGGER.error("Translation error: {}", e.getMessage());
                return finalText;
            }
        }, EXECUTOR);
    }

    private static String detectLanguage(String text) {
        for (Map.Entry<String, Pattern> entry : LANGUAGE_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(text).find()) {
                return entry.getKey();
            }
        }
        return "en";
    }

    private static String translateWithMyMemory(String text, String sourceLang, String targetLang) {
        try {
            // MyMemory API - ИСПРАВЛЕНО: правильно кодируем текст
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String url = String.format(
                    "https://api.mymemory.translated.net/get?q=%s&langpair=%s|%s",
                    encodedText,
                    sourceLang,
                    targetLang
            );

            LOGGER.debug("MyMemory request: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Minecraft-Chat-Translator/1.0")
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                // Логируем ответ для отладки
                LOGGER.debug("MyMemory response: {}", response.body());

                if (json.has("responseData")) {
                    String translated = json.get("responseData")
                            .getAsJsonObject()
                            .get("translatedText")
                            .getAsString();

                    // Проверяем, что перевод не пустой и не равен оригиналу
                    if (translated != null && !translated.isEmpty() && !translated.equals(text)) {
                        return translated;
                    }
                }
            } else {
                LOGGER.debug("MyMemory HTTP error: {}", response.statusCode());
            }

            return null;

        } catch (Exception e) {
            LOGGER.debug("MyMemory request failed: {}", e.getMessage());
            return null;
        }
    }

    private static String translateWithLingva(String text, String targetLang) {
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String url = String.format(
                    "https://lingva.ml/api/v1/auto/%s/%s",
                    targetLang,
                    encodedText
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Minecraft-Chat-Translator/1.0")
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("translation")) {
                    String translated = json.get("translation").getAsString();
                    if (!translated.equals(text)) {
                        return translated;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            LOGGER.debug("Lingva request failed: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isErrorMessage(String text) {
        return text.contains("INVALID") ||
                text.contains("ERROR") ||
                text.contains("AUTO IS AN INVALID") ||
                text.contains("CANNOT TRANSLATE") ||
                text.matches(".*\\b(error|failed)\\b.*");
    }

    public static void clearCache() {
        TRANSLATION_CACHE.clear();
        LOGGER.info("Translation cache cleared");
    }
}