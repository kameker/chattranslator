package kameker.chattranslator.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import kameker.chattranslator.Chattranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // Дополнительные настройки
    public static final ModConfigSpec.BooleanValue SHOW_ORIGINAL;
    public static final ModConfigSpec.ConfigValue<String> TRANSLATION_SERVICE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("General Settings");

        ENABLE_TRANSLATION = BUILDER
                .comment("Enable automatic chat translation")
                .define("enableTranslation", true);

        TRANSLATE_INCOMING = BUILDER
                .comment("Translate incoming messages")
                .define("translateIncoming", true);

        TRANSLATE_OUTGOING = BUILDER
                .comment("Translate outgoing messages (your messages)")
                .define("translateOutgoing", false);

        BUILDER.pop();

        BUILDER.push("Language Settings");

        AUTO_DETECT_SOURCE = BUILDER
                .comment("Auto-detect source language (if false, uses SOURCE_LANGUAGE)")
                .define("autoDetectSource", true);

        SOURCE_LANGUAGE = BUILDER
                .comment("Source language code (used when auto-detect is off)\n" +
                        "Available: en (English), ru (Russian), da (Danish), es (Spanish), fr (French), de (German), it (Italian), zh (Chinese), ja (Japanese), ko (Korean)")
                .define("sourceLanguage", "en");

        TARGET_LANGUAGE = BUILDER
                .comment("Target language code (translate to this language)\n" +
                        "Available: ru (Russian), en (English), da (Danish), es (Spanish), fr (French), de (German), it (Italian), zh (Chinese), ja (Japanese), ko (Korean)")
                .define("targetLanguage", "ru");

        BUILDER.pop();

        BUILDER.push("Display Settings");

        SHOW_ORIGINAL = BUILDER
                .comment("Show original message before translation")
                .define("showOriginal", true);

        TRANSLATION_SERVICE = BUILDER
                .comment("Translation service to use: mymemory, libretranslate, lingva")
                .define("translationService", "mymemory");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

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
        LOGGER.debug("Show original: {}", SHOW_ORIGINAL.get());
    }
}