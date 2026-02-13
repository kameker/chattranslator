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
    private static final long MESSAGE_TIMEOUT = 1000;
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
            // ИСПРАВЛЕНО: языки из конфига!
            String sourceLang = ModConfig.SOURCE_LANGUAGE.get();      // ru (русский)
            String targetLang = ModConfig.TARGET_LANGUAGE.get();      // en (английский)

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
        if (isProcessing) return;
        if (!ModConfig.ENABLE_TRANSLATION.get() || !ModConfig.TRANSLATE_INCOMING.get()) return;

        Component originalComponent = event.getMessage();
        String originalText = originalComponent.getString();

        if (shouldSkipMessage(originalText)) return;

        String messageHash = originalText + System.currentTimeMillis() / 1000;
        if (PROCESSED_MESSAGES.containsKey(messageHash)) return;

        PROCESSED_MESSAGES.put(messageHash, System.currentTimeMillis());
        isProcessing = true;

        try {
            // ИСПРАВЛЕНО: для входящих меняем местами
            String sourceLang = ModConfig.TARGET_LANGUAGE.get();      // en (с какого)
            String targetLang = ModConfig.SOURCE_LANGUAGE.get();      // ru (на какой)

            LOGGER.debug("Incoming translation: {} -> {}", sourceLang, targetLang);

            TranslationManager.translate(originalText, targetLang, sourceLang)
                    .thenAcceptAsync(translated -> {
                        Minecraft.getInstance().execute(() -> {
                            try {
                                if (translated == null || translated.equals(originalText)) {
                                    return;
                                }

                                Component newMessage;
                                if (ModConfig.SHOW_ORIGINAL.get()) {
                                    newMessage = Component.literal("")
                                            .append(Component.literal(originalText).withStyle(ChatFormatting.GRAY))
                                            .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                                            .append(Component.literal(translated).withStyle(ChatFormatting.WHITE));
                                    LOGGER.info("Вам перевели: {} -> {}", originalText, translated);
                                } else {
                                    newMessage = Component.literal(translated).withStyle(originalComponent.getStyle());
                                    LOGGER.info("Вам: {}", translated);
                                }

                                event.setMessage(newMessage);
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