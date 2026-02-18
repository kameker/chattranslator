package kameker.chattranslator.translation;

import com.google.gson.JsonArray;
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

                String translated = null;
                String service = ModConfig.TRANSLATION_SERVICE.get();

                // Выбираем сервис перевода
                switch (service) {
                    case "yandex":
                        LOGGER.debug("Using Yandex Translate");
                        translated = translateWithYandex(finalText, detectedSource, finalTargetLang);
                        break;
                    case "mymemory":
                        LOGGER.debug("Using MyMemory");
                        translated = translateWithMyMemory(finalText, detectedSource, finalTargetLang);
                        break;
                    default:
                        LOGGER.debug("Using MyMemory");
                        translated = translateWithMyMemory(finalText, detectedSource, finalTargetLang);
                        break;
                }

                if (translated != null && !translated.equals(finalText) && !isErrorMessage(translated)) {
                    translated = translated.replace("+", " ");
                    TRANSLATION_CACHE.put(cacheKey, translated);
                    LOGGER.info("Translated [{}->{}]: {} -> {}",
                            detectedSource, finalTargetLang, finalText, translated);
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

                if (json.has("responseData")) {
                    String translated = json.get("responseData")
                            .getAsJsonObject()
                            .get("translatedText")
                            .getAsString();

                    if (translated != null && !translated.isEmpty() && !translated.equals(text)) {
                        return translated;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            LOGGER.debug("MyMemory request failed: {}", e.getMessage());
            return null;
        }
    }


    // НОВОЕ: Метод для Yandex Translate
    private static String translateWithYandex(String text, String sourceLang, String targetLang) {
        try {
            String apiKey = ModConfig.YANDEX_API_KEY.get();
            String folderId = ModConfig.YANDEX_FOLDER_ID.get();

            // Проверяем, что ключи есть
            if (apiKey.isEmpty() || folderId.isEmpty()) {
                LOGGER.debug("Yandex API key or Folder ID not configured");
                return null;
            }

            String url = "https://translate.api.cloud.yandex.net/translate/v2/translate";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("folderId", folderId);

            // Текст для перевода
            JsonArray texts = new JsonArray();
            texts.add(text);
            requestBody.add("texts", texts);

            requestBody.addProperty("targetLanguageCode", targetLang);
            requestBody.addProperty("sourceLanguageCode", sourceLang);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Api-Key " + apiKey)
                    .header("User-Agent", "Minecraft-Chat-Translator/1.0")
                    .timeout(java.time.Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            LOGGER.debug("Yandex Translate request for: {}", text);

            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (json.has("translations")) {
                    String translated = json.get("translations")
                            .getAsJsonArray()
                            .get(0)
                            .getAsJsonObject()
                            .get("text")
                            .getAsString();

                    if (translated != null && !translated.isEmpty()) {
                        LOGGER.debug("Yandex Translate success: {} -> {}", text, translated);
                        return translated;
                    }
                }
            } else {
                LOGGER.debug("Yandex API error: {} - {}", response.statusCode(), response.body());
            }

            return null;

        } catch (Exception e) {
            LOGGER.debug("Yandex request failed: {}", e.getMessage());
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