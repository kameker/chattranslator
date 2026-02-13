package kameker.chattranslator;

import kameker.chattranslator.config.ModConfig;
import kameker.chattranslator.translation.TranslationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Chattranslator.MODID, value = Dist.CLIENT)
public class ChatHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatHandler.class);
    private static final Map<String, Long> PROCESSED_MESSAGES = new ConcurrentHashMap<>();
    private static final long MESSAGE_TIMEOUT = 2000;
    private static boolean isProcessing = false;

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        if (isProcessing) return;
        if (!ModConfig.ENABLE_TRANSLATION.get() || !ModConfig.TRANSLATE_OUTGOING.get()) return;

        String message = event.getMessage();
        if (message.startsWith("/")) return;

        String messageHash = message + System.currentTimeMillis() / 1000;
        if (PROCESSED_MESSAGES.containsKey(messageHash)) return;

        PROCESSED_MESSAGES.put(messageHash, System.currentTimeMillis());
        event.setCanceled(true);
        isProcessing = true;

        try {
            String sourceLang = ModConfig.SOURCE_LANGUAGE.get();      // ru
            String targetLang = ModConfig.TARGET_LANGUAGE.get();      // en

            LOGGER.debug("Outgoing translation: {} -> {}", sourceLang, targetLang);

            TranslationManager.translate(message, targetLang, sourceLang)
                    .thenAccept(translated -> {
                        Minecraft.getInstance().execute(() -> {
                            try {
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.connection.sendChat(translated);
                                    LOGGER.info("Ваше сообщение: {} -> {}", message, translated);
                                }
                            } finally {
                                isProcessing = false;
                                cleanProcessedMessages();
                            }
                        });
                    })
                    .exceptionally(e -> {
                        LOGGER.error("Translation failed: {}", e.getMessage());
                        isProcessing = false;
                        Minecraft.getInstance().execute(() -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.connection.sendChat(message);
                            }
                        });
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Error in chat handler: {}", e.getMessage());
            isProcessing = false;
        }
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        // Проверка на обработку
        if (isProcessing) {
            LOGGER.debug("Already processing, skipping received message");
            return;
        }

        if (!ModConfig.ENABLE_TRANSLATION.get()) {
            LOGGER.debug("Translation disabled");
            return;
        }

        if (!ModConfig.TRANSLATE_INCOMING.get()) {
            LOGGER.debug("Incoming translation disabled");
            return;
        }

        Component originalComponent = event.getMessage();
        String originalText = originalComponent.getString();

        LOGGER.info("Received message: {}", originalText);

        // Пропускаем системные сообщения
        if (shouldSkipMessage(originalText)) {
            LOGGER.debug("Skipping system message: {}", originalText);
            return;
        }

        // Проверяем, не обрабатывали ли мы это сообщение
        String messageHash = originalText + System.currentTimeMillis() / 1000;
        if (PROCESSED_MESSAGES.containsKey(messageHash)) {
            LOGGER.debug("Already processed this message");
            return;
        }

        PROCESSED_MESSAGES.put(messageHash, System.currentTimeMillis());
        isProcessing = true;

        try {
            // Для входящих: переводим с TARGET (en) на SOURCE (ru)
            String sourceLang = ModConfig.TARGET_LANGUAGE.get();      // en (с какого)
            String targetLang = ModConfig.SOURCE_LANGUAGE.get();      // ru (на какой)

            LOGGER.info("Incoming translation: {} -> {}", sourceLang, targetLang);

            TranslationManager.translate(originalText, targetLang, sourceLang)
                    .thenAcceptAsync(translated -> {
                        Minecraft.getInstance().execute(() -> {
                            try {
                                if (translated == null || translated.equals(originalText)) {
                                    LOGGER.debug("Перевод не нужен или не удался");
                                    return;
                                }

                                LOGGER.info("Переведено: {} -> {}", originalText, translated);

                                // ИСПРАВЛЕНО: Создаем новое сообщение
                                Component newMessage;
                                if (ModConfig.SHOW_ORIGINAL.get()) {
                                    newMessage = Component.literal("")
                                            .append(Component.literal(originalText).withStyle(ChatFormatting.GRAY))
                                            .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                                            .append(Component.literal(translated).withStyle(ChatFormatting.WHITE));
                                } else {
                                    newMessage = Component.literal(translated);
                                }

                                // ИСПРАВЛЕНО: Принудительно устанавливаем новое сообщение
                                event.setMessage(newMessage);

                                LOGGER.info("Сообщение в чате обновлено");

                            } finally {
                                isProcessing = false;
                                cleanProcessedMessages();
                            }
                        });
                    }, Minecraft.getInstance())
                    .exceptionally(e -> {
                        LOGGER.error("Translation error: {}", e.getMessage());
                        isProcessing = false;
                        return null;
                    });

        } catch (Exception e) {
            LOGGER.error("Error in chat handler: {}", e.getMessage());
            isProcessing = false;
        }
    }

    private static boolean shouldSkipMessage(String text) {
        if (text == null || text.isEmpty()) return true;

        // Пропускаем системные сообщения
        return text.contains("joined the game") ||
                text.contains("left the game") ||
                text.contains("вошел в игру") ||
                text.contains("вышел из игры") ||
                text.contains("[Server]") ||
                text.startsWith("!") ||
                text.startsWith("/") ||
                text.contains("★");
    }

    private static void cleanProcessedMessages() {
        long now = System.currentTimeMillis();
        PROCESSED_MESSAGES.entrySet().removeIf(entry ->
                now - entry.getValue() > MESSAGE_TIMEOUT);
    }
}