package kameker.chattranslator.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import kameker.chattranslator.Chattranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

@EventBusSubscriber(modid = Chattranslator.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConfig.class);

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Основные настройки
    public static final ModConfigSpec.BooleanValue ENABLE_TRANSLATION;
    public static final ModConfigSpec.BooleanValue TRANSLATE_INCOMING;
    public static final ModConfigSpec.BooleanValue TRANSLATE_OUTGOING;

    // Настройки языков
    public static final ModConfigSpec.ConfigValue<String> SOURCE_LANGUAGE;
    public static final ModConfigSpec.ConfigValue<String> TARGET_LANGUAGE;
    public static final ModConfigSpec.BooleanValue AUTO_DETECT_SOURCE;

    // Настройки перевода
    public static final ModConfigSpec.ConfigValue<String> TRANSLATION_SERVICE;
    public static final ModConfigSpec.ConfigValue<String> YANDEX_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> YANDEX_FOLDER_ID;
    public static final ModConfigSpec.ConfigValue<String> DEEPL_API_KEY;

    // Чёрный и белый списки
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_PLAYERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELISTED_PLAYERS;
    public static final ModConfigSpec.BooleanValue USE_WHITELIST_MODE;

    // Горячие клавиши
    public static final ModConfigSpec.IntValue HOTKEY_TOGGLE_TRANSLATION;
    public static final ModConfigSpec.BooleanValue HOTKEY_MODIFIER_SHIFT;
    public static final ModConfigSpec.BooleanValue HOTKEY_MODIFIER_CTRL;
    public static final ModConfigSpec.BooleanValue HOTKEY_MODIFIER_ALT;

    // Дополнительные настройки
    public static final ModConfigSpec.BooleanValue SHOW_ORIGINAL;
    public static final ModConfigSpec.BooleanValue SHOW_TRANSLATION_HINT;
    public static final ModConfigSpec.IntValue MESSAGE_TIMEOUT;
    public static final ModConfigSpec.IntValue CACHE_MAX_SIZE;

    static {
        BUILDER.push("Общие настройки");

        ENABLE_TRANSLATION = BUILDER
                .comment("Включить автоматический перевод чата")
                .define("enableTranslation", true);

        TRANSLATE_INCOMING = BUILDER
                .comment("Переводить входящие сообщения от других игроков")
                .define("translateIncoming", true);

        TRANSLATE_OUTGOING = BUILDER
                .comment("Переводить исходящие сообщения (ваши сообщения)")
                .define("translateOutgoing", false);

        MESSAGE_TIMEOUT = BUILDER
                .comment("Таймаут дубликатов сообщений в миллисекундах (защита от повторной обработки)\n" +
                        "Значение: 3000 мс = 3 секунды")
                .defineInRange("messageTimeout", 3000, 0, 60000);

        BUILDER.pop();

        BUILDER.push("Настройки языка");

        AUTO_DETECT_SOURCE = BUILDER
                .comment("Автоматически определять язык исходного сообщения\n" +
                        "Если false — используется язык из SOURCE_LANGUAGE")
                .define("autoDetectSource", true);

        SOURCE_LANGUAGE = BUILDER
                .comment("Код языка источника (используется при отключённом автоопределении)\n" +
                        "Доступные: en (Английский), ru (Русский), da (Датский), es (Испанский),\n" +
                        "fr (Французский), de (Немецкий), it (Итальянский), zh (Китайский),\n" +
                        "ja (Японский), ko (Корейский), pt (Португальский), pl (Польский), uk (Украинский)")
                .define("sourceLanguage", "en");

        TARGET_LANGUAGE = BUILDER
                .comment("Код целевого языка (на этот язык переводить)\n" +
                        "Доступные: en (Английский), ru (Русский), da (Датский), es (Испанский),\n" +
                        "fr (Французский), de (Немецкий), it (Итальянский), zh (Китайский),\n" +
                        "ja (Японский), ko (Корейский), pt (Португальский), pl (Польский), uk (Украинский)")
                .define("targetLanguage", "ru");

        BUILDER.pop();

        BUILDER.push("Сервисы перевода");

        TRANSLATION_SERVICE = BUILDER
                .comment("Сервис перевода: yandex, deepl, libre, lingva, mymemory\n" +
                        "Сервисы проверяются по порядку при включённом fallback")
                .define("translationService", "deepl");

        YANDEX_API_KEY = BUILDER
                .comment("API-ключ Яндекс.Переводчика (обязателен для сервиса Yandex)\n" +
                        "Получить: https://cloud.yandex.com/docs/translate/quickstart\n" +
                        "Требуется аккаунт в Яндекс.Облако и платёжный профиль (даётся грант)")
                .define("yandexApiKey", "");

        YANDEX_FOLDER_ID = BUILDER
                .comment("ID каталога Яндекс.Облако (обязателен для сервиса Yandex)\n" +
                        "Создаётся в консоли Яндекс.Облако при подключении Translate API")
                .define("yandexFolderId", "");

        DEEPL_API_KEY = BUILDER
                .comment("API-ключ DeepL (опционально, для более качественных переводов)\n" +
                        "Получить: https://www.deepl.com/pro-api?cta=header-pro-api\n" +
                        "Бесплатно: 500 000 символов/месяц")
                .define("deeplApiKey", "");

        BUILDER.pop();

        BUILDER.push("Фильтры игроков");

        USE_WHITELIST_MODE = BUILDER
                .comment("Если true — переводить только сообщения от игроков из белого списка.\n" +
                        "Если false — переводить все сообщения, кроме игроков из чёрного списка")
                .define("useWhitelistMode", false);

        WHITELISTED_PLAYERS = BUILDER
                .comment("Список имён игроков, от которых переводить сообщения (белый список)\n" +
                        "Оставьте пустым или добавьте имена через запятую")
                .defineList("whitelistedPlayers", () -> List.of(""), obj -> true);

        BLACKLISTED_PLAYERS = BUILDER
                .comment("Список имён игроков, от которых НЕ переводить сообщения (чёрный список)\n" +
                        "По умолчанию: Server, Admin")
                .defineList("blacklistedPlayers", () -> List.of("Server", "Admin"), obj -> true);

        BUILDER.pop();

        BUILDER.push("Настройки отображения");

        SHOW_ORIGINAL = BUILDER
                .comment("Показывать оригинальное сообщение перед переведённым")
                .define("showOriginal", true);

        SHOW_TRANSLATION_HINT = BUILDER
                .comment("Показывать подсказку о переводе с исходным/целевым языком")
                .define("showTranslationHint", true);

        CACHE_MAX_SIZE = BUILDER
                .comment("Максимальное количество переводов в кэше (LRU-кэш)\n" +
                        "При переполнении удаляются наименее используемые записи")
                .defineInRange("cacheMaxSize", 1000, 100, 10000);

        BUILDER.pop();

        BUILDER.push("Горячие клавиши");

        HOTKEY_TOGGLE_TRANSLATION = BUILDER
                .comment("Код клавиши для включения/выключения перевода (0 = отключено)\n" +
                        "Коды клавиш: https://www.glfw.org/docs/3.3/group__keys.html\n" +
                        "Пример: 340 = Shift, 341 = Ctrl, 342 = Alt, 82 = R")
                .defineInRange("hotkeyToggleTranslation", 0, 0, 350);

        HOTKEY_MODIFIER_SHIFT = BUILDER
                .comment("Требовать зажатый Shift для горячей клавиши")
                .define("hotkeyModifierShift", false);

        HOTKEY_MODIFIER_CTRL = BUILDER
                .comment("Требовать зажатый Ctrl для горячей клавиши")
                .define("hotkeyModifierCtrl", true);

        HOTKEY_MODIFIER_ALT = BUILDER
                .comment("Требовать зажатый Alt для горячей клавиши")
                .define("hotkeyModifierAlt", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static final ModConfigSpec SPEC;

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        LOGGER.info("Chat Translator config loaded");
        logConfig();
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        LOGGER.info("Chat Translator config reloaded");
        logConfig();
    }

    private static void logConfig() {
        LOGGER.debug("Source language: {} (auto-detect: {})",
                SOURCE_LANGUAGE.get(), AUTO_DETECT_SOURCE.get());
        LOGGER.debug("Target language: {}", TARGET_LANGUAGE.get());
        LOGGER.debug("Translation service: {}", TRANSLATION_SERVICE.get());
        LOGGER.debug("Show original: {}", SHOW_ORIGINAL.get());
        LOGGER.debug("Whitelist mode: {}", USE_WHITELIST_MODE.get());
        LOGGER.debug("Blacklisted players: {}", BLACKLISTED_PLAYERS.get());
        LOGGER.debug("Whitelisted players: {}", WHITELISTED_PLAYERS.get());
    }

    /**
     * Проверка, находится ли игрок в списке для перевода
     */
    public static boolean shouldTranslatePlayer(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return true;
        }

        if (USE_WHITELIST_MODE.get()) {
            return WHITELISTED_PLAYERS.get().contains(playerName);
        } else {
            return !BLACKLISTED_PLAYERS.get().contains(playerName);
        }
    }
}
