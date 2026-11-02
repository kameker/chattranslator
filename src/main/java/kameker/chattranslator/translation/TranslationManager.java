package kameker.chattranslator.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kameker.chattranslator.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TranslationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationManager.class);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "chattranslator-worker");
        thread.setDaemon(true);
        return thread;
    });

    // LRU кэш с ограничением размера
    private static final int DEFAULT_CACHE_SIZE = 1000;
    private static final TranslationCache TRANSLATION_CACHE = new TranslationCache(DEFAULT_CACHE_SIZE);
    private static final AtomicBoolean CACHE_INITIALIZED = new AtomicBoolean();

    // Rate limiting
    private static final Map<String, RateLimiter> RATE_LIMITERS = new ConcurrentHashMap<>();
    private static final int DEFAULT_RATE_LIMIT = 50; // запросов в минуту
    private static final int DEEPL_RATE_LIMIT = 20;   // DeepL имеет более строгие лимиты

    // Приоритет сервисов для fallback
    private static final List<String> SERVICE_PRIORITY = Arrays.asList(
            "yandex", "deepl", "libre", "lingva", "mymemory"
    );

    static {
        // Инициализация rate limiters
        RATE_LIMITERS.put("mymemory", new RateLimiter(DEFAULT_RATE_LIMIT, 60));
        RATE_LIMITERS.put("libre", new RateLimiter(DEFAULT_RATE_LIMIT, 60));
        RATE_LIMITERS.put("lingva", new RateLimiter(DEFAULT_RATE_LIMIT, 60));
        RATE_LIMITERS.put("yandex", new RateLimiter(DEFAULT_RATE_LIMIT, 60));
        RATE_LIMITERS.put("deepl", new RateLimiter(DEEPL_RATE_LIMIT, 60));

    }

    public record TranslationRequest(String text, String sourceLanguage, String targetLanguage) {
        public TranslationRequest {
            if (targetLanguage == null || targetLanguage.isBlank()) {
                throw new IllegalArgumentException("Target language must not be blank");
            }
        }
    }

    /**
     * Основной метод перевода с fallback между сервисами
     */
    public static CompletableFuture<String> translate(TranslationRequest request) {
        Objects.requireNonNull(request, "request");
        return CompletableFuture.supplyAsync(() -> translateSync(
                request.text(), request.sourceLanguage(), request.targetLanguage()), EXECUTOR);
    }

    static String translateSync(String text, String sourceLang, String targetLang) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return text;
            }
            if (targetLang == null || targetLang.isBlank()) {
                throw new IllegalArgumentException("Target language must not be blank");
            }

            ensureCacheInitialized();
            TRANSLATION_CACHE.resize(ModConfig.CACHE_MAX_SIZE.get());

            String detectedSource = sourceLang;
            if (detectedSource == null || detectedSource.isBlank() || ModConfig.AUTO_DETECT_SOURCE.get()) {
                detectedSource = TranslationEngine.detectLanguage(text);
                LOGGER.debug("Detected source language: {}", detectedSource);
            }

            String cacheKey = text + "|" + detectedSource + "|" + targetLang;
            String cached = TRANSLATION_CACHE.get(cacheKey);
            if (cached != null) {
                LOGGER.debug("Cache hit: {}", cacheKey);
                return cached;
            }

            if (detectedSource.equalsIgnoreCase(targetLang)) {
                LOGGER.debug("Text already in target language");
                return text;
            }

            String translated = text.length() > 4000
                    ? translateLongText(text, detectedSource, targetLang)
                    : translateWithFallback(text, detectedSource, targetLang);

            if (translated != null && !translated.equals(text) && !TranslationEngine.isErrorMessage(translated)) {
                TRANSLATION_CACHE.put(cacheKey, translated);
                LOGGER.info("Translated [{}->{}]: {} -> {}",
                        detectedSource, targetLang, text, translated);
                return translated;
            }
            return text;
        } catch (Exception e) {
            LOGGER.error("Translation error: {}", e.getMessage(), e);
            return text;
        }
    }

    /**
     * Перевод длинных текстов с разбиением на части
     */
    private static String translateLongText(String text, String sourceLang, String targetLang) {
        LOGGER.debug("Translating long text ({} chars)", text.length());

        StringBuilder result = new StringBuilder();

        for (String chunk : TranslationEngine.splitText(text, 4000)) {
            String translated = translateWithFallback(chunk, sourceLang, targetLang);
            if (translated == null) {
                translated = chunk;
            }
            if (!result.isEmpty()) result.append(' ');
            result.append(translated);
        }

        return result.toString().trim();
    }

    /**
     * Перевод с fallback между сервисами
     */
    private static String translateWithFallback(String text, String sourceLang, String targetLang) {
        String preferredService = ModConfig.TRANSLATION_SERVICE.get();
        return translateWithFallback(text, sourceLang, targetLang, preferredService,
                createBuiltInServices(), SERVICE_PRIORITY, RATE_LIMITERS);
    }

    static String translateWithFallback(String text, String sourceLang, String targetLang,
                                        String preferredService,
                                        Map<String, TranslationEngine.Service> services,
                                        List<String> priority,
                                        Map<String, RateLimiter> rateLimiters) {
        String translated = TranslationEngine.translateWithFallback(
                text, sourceLang, targetLang, preferredService, services, priority, service -> {
                    RateLimiter limiter = rateLimiters.get(service);
                    boolean allowed = limiter == null || limiter.tryAcquire();
                    if (!allowed) LOGGER.debug("Rate limit exceeded for {}", service);
                    return allowed;
                });
        if (translated == null) LOGGER.warn("All translation services failed");
        return translated;
    }

    private static Map<String, TranslationEngine.Service> createBuiltInServices() {
        Map<String, TranslationEngine.Service> services = new HashMap<>();
        services.put("yandex", TranslationManager::translateWithYandex);
        services.put("deepl", TranslationManager::translateWithDeepL);
        services.put("libre", TranslationManager::translateWithLibre);
        services.put("lingva", (text, source, target) -> translateWithLingva(text, target));
        services.put("mymemory", TranslationManager::translateWithMyMemory);
        return services;
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Minecraft-Chat-Translator/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (json.has("responseData")) {
                    JsonObject responseData = json.getAsJsonObject("responseData");
                    if (responseData.has("translatedText")) {
                        return responseData.get("translatedText").getAsString();
                    }
                }
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
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("translation")) {
                    return json.get("translation").getAsString();
                }
            }

            return null;

        } catch (Exception e) {
            LOGGER.debug("Lingva request failed: {}", e.getMessage());
            return null;
        }
    }

    private static String translateWithLibre(String text, String sourceLang, String targetLang) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("q", text);
            requestBody.addProperty("source", sourceLang);
            requestBody.addProperty("target", targetLang);
            requestBody.addProperty("format", "text");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://libretranslate.com/translate"))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Minecraft-Chat-Translator/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("translatedText")) {
                    return json.get("translatedText").getAsString();
                }
            }

            return null;

        } catch (Exception e) {
            LOGGER.debug("LibreTranslate request failed: {}", e.getMessage());
            return null;
        }
    }

    private static String translateWithYandex(String text, String sourceLang, String targetLang) {
        try {
            String apiKey = ModConfig.YANDEX_API_KEY.get();
            String folderId = ModConfig.YANDEX_FOLDER_ID.get();

            if (apiKey.isEmpty() || folderId.isEmpty()) {
                LOGGER.debug("Yandex API key or Folder ID not configured");
                return null;
            }

            String url = "https://translate.api.cloud.yandex.net/translate/v2/translate";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("folderId", folderId);

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
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (json.has("translations")) {
                    return json.getAsJsonArray("translations")
                            .get(0)
                            .getAsJsonObject()
                            .get("text")
                            .getAsString();
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

    /**
     * Перевод через DeepL API
     */
    private static String translateWithDeepL(String text, String sourceLang, String targetLang) {
        try {
            String apiKey = ModConfig.DEEPL_API_KEY.get();

            if (apiKey.isEmpty()) {
                LOGGER.debug("DeepL API key not configured");
                return null;
            }

            String url = "https://api-free.deepl.com/v2/translate";

            JsonObject requestBody = new JsonObject();
            JsonArray texts = new JsonArray();
            texts.add(text);
            requestBody.add("texts", texts);

            requestBody.addProperty("source_lang", sourceLang.toUpperCase());
            requestBody.addProperty("target_lang", targetLang.toUpperCase());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "DeepL-Auth-Key " + apiKey)
                    .header("User-Agent", "Minecraft-Chat-Translator/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (json.has("translations")) {
                    JsonArray translations = json.getAsJsonArray("translations");
                    StringBuilder result = new StringBuilder();

                    for (int i = 0; i < translations.size(); i++) {
                        if (i > 0) result.append(" ");
                        result.append(translations.get(i)
                                .getAsJsonObject()
                                .get("text")
                                .getAsString());
                    }

                    return result.toString();
                }
            } else {
                LOGGER.debug("DeepL API error: {} - {}", response.statusCode(), response.body());
            }

            return null;

        } catch (Exception e) {
            LOGGER.debug("DeepL request failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Загрузка кэша из файла
     */
    private static void loadCacheFromFile() {
        try {
            Path cachePath = getCacheFile();
            File cacheFile = cachePath.toFile();
            if (!cacheFile.exists()) {
                LOGGER.debug("Cache file does not exist, starting fresh");
                return;
            }

            String json = new String(java.nio.file.Files.readAllBytes(cachePath), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("translations")) {
                JsonObject translations = root.getAsJsonObject("translations");
                for (Map.Entry<String, com.google.gson.JsonElement> entry : translations.entrySet()) {
                    TRANSLATION_CACHE.put(entry.getKey(), entry.getValue().getAsString());
                }
                LOGGER.info("Loaded {} cached translations", translations.size());
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to load cache from file: {}", e.getMessage());
        }
    }

    /**
     * Сохранение кэша в файл
     */
    private static void saveCacheToFile() {
        try {
            JsonObject root = new JsonObject();
            JsonObject translations = new JsonObject();

            for (Map.Entry<String, String> entry : TRANSLATION_CACHE.snapshot().entrySet()) {
                translations.addProperty(entry.getKey(), entry.getValue());
            }

            root.add("translations", translations);
            root.addProperty("version", 1);
            root.addProperty("timestamp", System.currentTimeMillis());

            String json = root.toString();
            java.nio.file.Files.writeString(getCacheFile(), json, StandardCharsets.UTF_8);

            LOGGER.debug("Saved {} translations to cache file", TRANSLATION_CACHE.size());

        } catch (Exception e) {
            LOGGER.warn("Failed to save cache to file: {}", e.getMessage());
        }
    }

    private static Path getCacheFile() {
        return FMLPaths.GAMEDIR.get().resolve("chattranslator_cache.json");
    }

    private static void ensureCacheInitialized() {
        if (!CACHE_INITIALIZED.compareAndSet(false, true)) return;
        loadCacheFromFile();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chattranslator-cache");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(TranslationManager::saveCacheToFile, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Очистка кэша
     */
    public static void clearCache() {
        TRANSLATION_CACHE.clear();
        saveCacheToFile();
        LOGGER.info("Translation cache cleared");
    }

    /**
     * Получить статистику кэша
     */
    public static Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("size", TRANSLATION_CACHE.size());
        stats.put("maxSize", TRANSLATION_CACHE.maxSize());
        stats.put("hitRate", TRANSLATION_CACHE.getHitRate());
        return stats;
    }

    /**
     * LRU кэш с ограничением размера и подсчётом hit rate (package-private для тестов)
     */
    static class TranslationCache {
        private final LinkedHashMap<String, String> cache;
        private int maxSize;
        private int hits = 0;
        private int misses = 0;

        public TranslationCache(int maxSize) {
            this.maxSize = maxSize;
            this.cache = new LinkedHashMap<String, String>(maxSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > maxSize;
                }
            };
        }

        public synchronized String get(String key) {
            String value = cache.get(key);
            if (value != null) {
                hits++;
            } else {
                misses++;
            }
            return value;
        }

        public synchronized void put(String key, String value) {
            cache.put(key, value);
        }

        public synchronized void resize(int newMaxSize) {
            if (newMaxSize <= 0) throw new IllegalArgumentException("Cache size must be positive");
            maxSize = newMaxSize;
            while (cache.size() > maxSize) {
                Iterator<String> iterator = cache.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }

        public synchronized int maxSize() {
            return maxSize;
        }

        public synchronized double getHitRate() {
            int total = hits + misses;
            if (total == 0) return 0.0;
            return (double) hits / total * 100;
        }

        public synchronized int size() {
            return cache.size();
        }

        public synchronized void clear() {
            cache.clear();
            hits = 0;
            misses = 0;
        }

        public synchronized Map<String, String> snapshot() {
            return new LinkedHashMap<>(cache);
        }
    }

    /**
     * Rate limiter для ограничения частоты запросов (package-private для тестов)
     */
    static class RateLimiter {
        private final int maxRequests;
        private final long timeWindowMs;
        private final Queue<Long> requestTimes = new ConcurrentLinkedQueue<>();

        public RateLimiter(int maxRequests, long timeWindowSeconds) {
            this.maxRequests = maxRequests;
            this.timeWindowMs = timeWindowSeconds * 1000;
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();

            // Удаляем старые запросы за пределами временного окна
            requestTimes.removeIf(time -> now - time > timeWindowMs);

            if (requestTimes.size() < maxRequests) {
                requestTimes.add(now);
                return true;
            }

            return false;
        }

        public synchronized long getWaitTime() {
            long now = System.currentTimeMillis();
            Long oldestRequest = requestTimes.peek();

            if (oldestRequest == null) {
                return 0;
            }

            long waitTime = timeWindowMs - (now - oldestRequest);
            return Math.max(0, waitTime);
        }
    }
}
