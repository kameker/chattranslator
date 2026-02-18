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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Chattranslator.MODID, value = Dist.CLIENT)
public class ChatHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatHandler.class);
    private static final Map<String, Long> PROCESSED_MESSAGES = new ConcurrentHashMap<>();
    private static final long MESSAGE_TIMEOUT = 0; // Увеличил до 3 секунд
    private static boolean isProcessing = false;
    private static String lastSentMessage = "";
    private static String lastSentTranslation = ""; // Запоминаем перевод

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        if (isProcessing) return;
        if (!ModConfig.ENABLE_TRANSLATION.get() || !ModConfig.TRANSLATE_OUTGOING.get()) return;

        String message = event.getMessage();
        if (message.startsWith("/")) return;
        if (message.startsWith("!")) return;
        String messageHash = message + System.currentTimeMillis() / 1000;
        if (PROCESSED_MESSAGES.containsKey(messageHash)) return;

        lastSentMessage = message;
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
                                    lastSentTranslation = translated; // Запоминаем перевод
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
    private static String stripColorCodes(String text) {
        // Удаляет все §-коды (цвета и форматирование)
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        if (isProcessing) {
            LOGGER.debug("Уже обрабатывается, пропускаем");
            return;
        }

        if (!ModConfig.ENABLE_TRANSLATION.get() || !ModConfig.TRANSLATE_INCOMING.get()) {
            return;
        }

        Component originalComponent = event.getMessage();
        String fullMessage = originalComponent.getString();
        fullMessage = stripColorCodes(fullMessage);
        LOGGER.debug(fullMessage);
        if (fullMessage.startsWith("!")) return;
        if (fullMessage.startsWith("Пер")) return;
        // Пропускаем системные сообщения
        if (shouldSkipMessage(fullMessage)) {
            return;
        }

        // УЛУЧШЕНО: Пропускаем свои сообщения
        if (!lastSentMessage.isEmpty() && fullMessage.contains(lastSentMessage)) {
            LOGGER.debug("Пропускаем своё сообщение (оригинал)");
            return;
        }

        // Также пропускаем переводы своих сообщений
        if (!lastSentTranslation.isEmpty() && fullMessage.contains(lastSentTranslation)) {
            LOGGER.debug("Пропускаем своё сообщение (перевод)");
            return;
        }

        String messageHash = fullMessage + System.currentTimeMillis() / 1000;
        if (PROCESSED_MESSAGES.containsKey(messageHash)) {
            LOGGER.debug("Сообщение уже обработано");
            return;
        }

        PROCESSED_MESSAGES.put(messageHash, System.currentTimeMillis());
        isProcessing = true;

        try {
            int colonIndex = fullMessage.indexOf(':')+1;

            String prefix;
            String textToTranslate;
            LOGGER.debug(Arrays.toString(fullMessage.split(" ")));
            if (colonIndex != -1) {
                // Есть двоеточие - разделяем
                prefix = ""; // Включаем двоеточие
                textToTranslate = fullMessage.substring(colonIndex).trim();
            } else {
                prefix = "";
                textToTranslate = fullMessage;
            }

            LOGGER.debug("Префикс: {}", prefix);
            LOGGER.debug("Текст для перевода: {}", textToTranslate);
            String sourceLang = ModConfig.SOURCE_LANGUAGE.get();      // ru
            String targetLang = ModConfig.TARGET_LANGUAGE.get();      // en

            TranslationManager.translate(textToTranslate, sourceLang, targetLang)
                    .thenAcceptAsync(translated -> {
                        Minecraft.getInstance().execute(() -> {
                            try {
                                if (translated == null || translated.equals(textToTranslate)) {
                                    LOGGER.debug("Перевод не удался");
                                    return;
                                }
                                if (translated.equals(textToTranslate)) {
                                    LOGGER.debug("Перевод не нужен");
                                    return;
                                }

                                LOGGER.info("Получено (английский): {}", textToTranslate);
                                LOGGER.info("Перевод (русский): {}", translated);

                                if (Minecraft.getInstance().player != null) {
                                    String finalTranslated;
                                    if (colonIndex != -1) {
                                        finalTranslated = prefix + " " + translated;
                                    } else {
                                        finalTranslated = translated;
                                    }

                                    Component translatedMessage = Component.literal("")
                                            .append(Component.literal("Перевод").withStyle(ChatFormatting.RED))
                                            .append(Component.literal(finalTranslated).withStyle(ChatFormatting.WHITE));

                                    Minecraft.getInstance().player.sendSystemMessage(translatedMessage);
                                }

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
                text.contains("получил") ||
                text.contains("был") ||
                text.contains("just join") ||
                text.contains("left the game") ||
                text.contains("вошел в игру") ||
                text.contains("присоединился") ||
                text.contains("Remember to check the rules. Failing to comply may result in a ban!") ||
                text.contains("Check out the RP bonuses on the Discord") ||
                text.contains("Make sure to create a faction to keep your lands safe") ||
                text.contains("Take a look at the webmap by clicking here") ||
                text.contains("Haven't joined our Discord yet? Click on this message to join!") ||
                text.contains("Faction color updated to white") ||
                text.contains("вышел из игры") ||
                text.contains("игре") ||
                text.contains("покинул") ||
                text.contains("проспите") ||
                text.contains("ночь") ||
                text.contains("[Server]") ||
                text.contains("застрелен") ||
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


