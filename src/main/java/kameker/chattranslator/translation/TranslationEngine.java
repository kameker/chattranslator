package kameker.chattranslator.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

final class TranslationEngine {
    private static final Pattern JAPANESE = Pattern.compile("[\\u3040-\\u30ff]");
    private static final Pattern KOREAN = Pattern.compile("[\\uac00-\\ud7af\\u1100-\\u11ff]");
    private static final Pattern CHINESE = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern UKRAINIAN = Pattern.compile("[ієїґІЄЇҐ]");
    private static final Pattern CYRILLIC = Pattern.compile("[а-яА-ЯёЁ]");

    private static final Map<String, Pattern> DISTINCTIVE_LATIN = Map.of(
            "da", Pattern.compile("[æøåÆØÅ]"),
            "es", Pattern.compile("[áéíóúñü¿¡]", Pattern.CASE_INSENSITIVE),
            "fr", Pattern.compile("[àâæçéèêëîïôœùûüÿ]", Pattern.CASE_INSENSITIVE),
            "de", Pattern.compile("[äöüß]", Pattern.CASE_INSENSITIVE),
            "it", Pattern.compile("[àèìòù]", Pattern.CASE_INSENSITIVE),
            "pt", Pattern.compile("[ãõçáéíóú]", Pattern.CASE_INSENSITIVE),
            "pl", Pattern.compile("[ąćęłńóśźż]", Pattern.CASE_INSENSITIVE)
    );

    @FunctionalInterface
    interface Service {
        String translate(String text, String sourceLang, String targetLang) throws Exception;
    }

    private TranslationEngine() {
    }

    static String detectLanguage(String text) {
        if (text == null || text.isBlank()) return "en";
        if (JAPANESE.matcher(text).find()) return "ja";
        if (KOREAN.matcher(text).find()) return "ko";
        if (CHINESE.matcher(text).find()) return "zh";
        if (UKRAINIAN.matcher(text).find()) return "uk";
        if (CYRILLIC.matcher(text).find()) return "ru";

        String bestLanguage = "en";
        int bestScore = 0;
        for (Map.Entry<String, Pattern> entry : DISTINCTIVE_LATIN.entrySet()) {
            int score = 0;
            var matcher = entry.getValue().matcher(text);
            while (matcher.find()) score++;
            if (score > bestScore) {
                bestLanguage = entry.getKey();
                bestScore = score;
            }
        }
        return bestLanguage;
    }

    static boolean isErrorMessage(String text) {
        if (text == null || text.isBlank()) return true;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("invalid")
                || lower.contains("error")
                || lower.contains("cannot translate")
                || lower.contains("too many requests")
                || lower.contains("rate limit")
                || lower.matches(".*\\bfailed\\b.*");
    }

    static String translateWithFallback(String text, String sourceLang, String targetLang,
                                        String preferredService, Map<String, Service> services,
                                        List<String> priority, Predicate<String> canCall) {
        List<String> servicesToTry = new ArrayList<>();
        if (preferredService != null && !preferredService.isBlank()) servicesToTry.add(preferredService);
        for (String service : priority) {
            if (!servicesToTry.contains(service)) servicesToTry.add(service);
        }

        for (String serviceName : servicesToTry) {
            Service service = services.get(serviceName);
            if (service == null || !canCall.test(serviceName)) continue;
            try {
                String translated = service.translate(text, sourceLang, targetLang);
                if (translated != null && !isErrorMessage(translated)) return translated;
            } catch (Exception ignored) {
                // The next configured service is the recovery path.
            }
        }
        return null;
    }

    static List<String> splitText(String text, int maxLength) {
        if (maxLength <= 0) throw new IllegalArgumentException("Maximum length must be positive");
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxLength);
            if (end < text.length()) {
                int boundary = text.lastIndexOf(' ', end);
                if (boundary > start) end = boundary;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            start = end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        }
        return chunks;
    }
}
