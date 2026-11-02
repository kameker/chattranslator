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
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = Chattranslator.MODID, value = Dist.CLIENT)
public class ChatHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatHandler.class);
    private static final MessageDeduplicator PROCESSED_MESSAGES = new MessageDeduplicator();
    private static String lastSentMessage = "";
    private static String lastSentTranslation = "";

    // История переводов
    private static final TranslationHistory TRANSLATION_HISTORY = new TranslationHistory(50);

    // Паттерн для извлечения имени игрока из сообщения
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^<([^>]+)>.*");

    // Состояние перевода
    private static boolean translationEnabled = true;
    private static long lastToggleTime = 0;

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();

        // Обработка команд истории
        if (message.equals("!translate history") || message.equals("!th")) {
            event.setCanceled(true);
            showTranslationHistory();
            return;
        }

        if (message.equals("!translate clear") || message.equals("!tc")) {
            event.setCanceled(true);
            TRANSLATION_HISTORY.clear();
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal("[ChatTranslator] История переводов очищена")
                                .withStyle(ChatFormatting.YELLOW)
                );
            }
            return;
        }

        if (message.equals("!translate stats") || message.equals("!ts")) {
            event.setCanceled(true);
            showCacheStats();
            return;
        }

        if (!isTranslationEnabled()) return;
        if (!ModConfig.ENABLE_TRANSLATION.get() || !ModConfig.TRANSLATE_OUTGOING.get()) return;

        if (message.startsWith("/") || message.startsWith("!")) return;

        // Проверяем дубликаты
        String messageHash = "outgoing|" + message;
        if (!markAsProcessing(messageHash)) return;

        lastSentMessage = message;
        event.setCanceled(true);

        try {
            String sourceLang = ModConfig.SOURCE_LANGUAGE.get();
            String targetLang = ModConfig.TARGET_LANGUAGE.get();

            LOGGER.debug("Outgoing translation: {} -> {}", sourceLang, targetLang);

            TranslationManager.translate(new TranslationManager.TranslationRequest(
                            message, sourceLang, targetLang))
                    .thenAccept(translated -> {
                        Minecraft.getInstance().execute(() -> {
                            try {
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.connection.sendChat(translated);
                                    lastSentTranslation = translated;
                                    LOGGER.info("Ваше сообщение: {} -> {}", message, translated);

                                    // Добавляем в историю
                                    TRANSLATION_HISTORY.add(message, translated, true);
                                }
                            } finally {
                                cleanProcessedMessages();
                            }
                        });
                    })
                    .exceptionally(e -> {
                        LOGGER.error("Translation failed: {}", e.getMessage());
                        Minecraft.getInstance().execute(() -> {
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.connection.sendChat(message);
                            }
                        });
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Error in chat handler: {}", e.getMessage());
            PROCESSED_MESSAGES.remove(messageHash);
        }
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        if (!isTranslationEnabled()) return;
        if (!ModConfig.ENABLE_TRANSLATION.get() || !ModConfig.TRANSLATE_INCOMING.get()) return;

        Component originalComponent = event.getMessage();
        String fullMessage = stripColorCodes(originalComponent.getString());

        if (fullMessage.startsWith("!") || fullMessage.startsWith("/") || fullMessage.startsWith("Пер")) {
            return;
        }

        // Извлекаем имя игрока
        String playerName = extractPlayerName(fullMessage);

        if (playerName != null && Minecraft.getInstance().player != null
                && playerName.equalsIgnoreCase(Minecraft.getInstance().player.getName().getString())) {
            return;
        }

        // Проверяем фильтры игроков
        if (playerName != null && !ModConfig.shouldTranslatePlayer(playerName)) {
            LOGGER.debug("Skipping message from blacklisted/whitelisted player: {}", playerName);
            return;
        }

        // Пропускаем системные сообщения
        if (shouldSkipMessage(fullMessage)) {
            return;
        }

        // Пропускаем свои сообщения
        if (!lastSentMessage.isEmpty() && fullMessage.contains(lastSentMessage)) {
            LOGGER.debug("Пропускаем своё сообщение (оригинал)");
            return;
        }

        if (!lastSentTranslation.isEmpty() && fullMessage.contains(lastSentTranslation)) {
            LOGGER.debug("Пропускаем своё сообщение (перевод)");
            return;
        }

        // Проверяем дубликаты
        String messageHash = "incoming|" + fullMessage;
        if (!markAsProcessing(messageHash)) {
            LOGGER.debug("Сообщение уже обработано");
            return;
        }

        try {
            // Разделяем префикс и текст
            final int colonIndex = fullMessage.indexOf(':');
            final String prefix = colonIndex != -1 ? fullMessage.substring(0, colonIndex + 1) : "";
            final String textToTranslate = colonIndex != -1 ? fullMessage.substring(colonIndex + 1).trim() : fullMessage;

            String sourceLang = ModConfig.SOURCE_LANGUAGE.get();
            String targetLang = ModConfig.TARGET_LANGUAGE.get();

            TranslationManager.translate(new TranslationManager.TranslationRequest(
                            textToTranslate, sourceLang, targetLang))
                    .thenAcceptAsync(translated -> {
                        Minecraft.getInstance().execute(() -> {
                            try {
                                if (translated == null || translated.equals(textToTranslate)) {
                                    LOGGER.debug("Перевод не удался или не нужен");
                                    return;
                                }

                                LOGGER.info("Получено: {}", textToTranslate);
                                LOGGER.info("Перевод: {}", translated);

                                if (Minecraft.getInstance().player != null) {
                                    String finalTranslated = prefix + translated;

                                    // Формируем сообщение с переводом
                                    Component translatedMessage = createTranslationComponent(
                                            finalTranslated,
                                            textToTranslate,
                                            ModConfig.SHOW_ORIGINAL.get(),
                                            ModConfig.SHOW_TRANSLATION_HINT.get(),
                                            ModConfig.AUTO_DETECT_SOURCE.get() ? "auto" : sourceLang,
                                            targetLang
                                    );

                                    Minecraft.getInstance().player.sendSystemMessage(translatedMessage);

                                    // Добавляем в историю
                                    TRANSLATION_HISTORY.add(textToTranslate, translated, false);
                                }

                            } finally {
                                cleanProcessedMessages();
                            }
                        });
                    }, Minecraft.getInstance())
                    .exceptionally(e -> {
                        LOGGER.error("Translation error: {}", e.getMessage());
                        PROCESSED_MESSAGES.remove(messageHash);
                        return null;
                    });

        } catch (Exception e) {
            LOGGER.error("Error in chat handler: {}", e.getMessage());
            PROCESSED_MESSAGES.remove(messageHash);
        }
    }

    /**
     * Обработка горячих клавиш
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        int hotkey = ModConfig.HOTKEY_TOGGLE_TRANSLATION.get();

        if (hotkey <= 0) return; // Горячая клавиша отключена

        // Проверяем модификаторы
        boolean shiftPressed = GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) == 1
                || GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
        boolean ctrlPressed = GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == 1
                || GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL) == 1;
        boolean altPressed = GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == 1
                || GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_ALT) == 1;

        boolean needShift = ModConfig.HOTKEY_MODIFIER_SHIFT.get();
        boolean needCtrl = ModConfig.HOTKEY_MODIFIER_CTRL.get();
        boolean needAlt = ModConfig.HOTKEY_MODIFIER_ALT.get();

        if (event.getKey() != hotkey) return;
        if (shiftPressed != needShift || ctrlPressed != needCtrl || altPressed != needAlt) return;

        // Debounce - защита от повторных нажатий
        long now = System.currentTimeMillis();
        if (now - lastToggleTime < 500) return;
        lastToggleTime = now;

        // Переключаем состояние
        translationEnabled = !translationEnabled;

        // Уведомляем игрока
        if (Minecraft.getInstance().player != null) {
            Component statusMessage = Component.literal(
                    translationEnabled ? "[ChatTranslator] Перевод включён" : "[ChatTranslator] Перевод выключен"
            ).withStyle(translationEnabled ? ChatFormatting.GREEN : ChatFormatting.RED);

            Minecraft.getInstance().player.sendSystemMessage(statusMessage);
        }

        LOGGER.info("Translation toggled: {}", translationEnabled);
    }

    private static void showTranslationHistory() {
        if (Minecraft.getInstance().player == null) return;

        Minecraft.getInstance().player.sendSystemMessage(
                Component.literal("=== История переводов ===").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD)
        );

        for (TranslationHistory.Entry entry : TRANSLATION_HISTORY.getEntries()) {
            String direction = entry.isOutgoing ? "→" : "←";
            ChatFormatting color = entry.isOutgoing ? ChatFormatting.GREEN : ChatFormatting.AQUA;

            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal(direction + " " + entry.original)
                            .withStyle(ChatFormatting.GRAY)
            );
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("  " + entry.translated)
                            .withStyle(color)
            );
        }

        if (TRANSLATION_HISTORY.size() == 0) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("История пуста").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
            );
        }
    }

    private static void showCacheStats() {
        if (Minecraft.getInstance().player == null) return;

        Map<String, Object> stats = TranslationManager.getCacheStats();

        Minecraft.getInstance().player.sendSystemMessage(
                Component.literal("=== Статистика кэша ===").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD)
        );
        Minecraft.getInstance().player.sendSystemMessage(
                Component.literal("Размер: " + stats.get("size") + " / " + stats.get("maxSize"))
                        .withStyle(ChatFormatting.WHITE)
        );
        Minecraft.getInstance().player.sendSystemMessage(
                Component.literal("Hit Rate: " + String.format("%.1f", (Double) stats.get("hitRate")) + "%")
                        .withStyle(ChatFormatting.WHITE)
        );
    }

    private static boolean isTranslationEnabled() {
        return translationEnabled;
    }

    public static void setTranslationEnabled(boolean enabled) {
        translationEnabled = enabled;
    }

    public static boolean isTranslationToggled() {
        return translationEnabled;
    }

    private static String extractPlayerName(String message) {
        Matcher matcher = PLAYER_NAME_PATTERN.matcher(message);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    private static Component createTranslationComponent(String translated, String original, boolean showOriginal,
                                                        boolean showHint, String sourceLang, String targetLang) {
        StringBuilder result = new StringBuilder();

        // Добавляем префикс
        result.append("[Перевод");
        if (showHint) {
            result.append(" ").append(sourceLang).append("→").append(targetLang);
        }
        result.append("] ");

        // Добавляем перевод
        result.append(translated);

        Component message = Component.literal(result.toString()).withStyle(ChatFormatting.WHITE);

        // Если нужно, добавляем тултип с оригиналом
        if (showOriginal && !original.equals(translated)) {
            String prefix = showHint
                    ? "[Перевод " + sourceLang + "→" + targetLang + "] "
                    : "[Перевод] ";
            message = Component.literal(prefix)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal(translated).withStyle(ChatFormatting.WHITE)
                            .setStyle(Component.literal(original).getStyle()
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Оригинал: " + original)
                                                    .withStyle(ChatFormatting.GRAY)
                                    ))
                            )
                    );
        }

        return message;
    }

    private static String stripColorCodes(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
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
        long timeout = ModConfig.MESSAGE_TIMEOUT.get();
        PROCESSED_MESSAGES.clean(System.currentTimeMillis(), timeout);
    }

    static boolean markAsProcessing(String messageHash) {
        long timeout = ModConfig.MESSAGE_TIMEOUT.get();
        cleanProcessedMessages();
        return PROCESSED_MESSAGES.markIfNew(messageHash, System.currentTimeMillis(), timeout);
    }

    /**
     * История переводов с ограничением размера
     */
    private static class TranslationHistory {
        private final java.util.Deque<Entry> history;
        private final int maxSize;

        public TranslationHistory(int maxSize) {
            this.maxSize = maxSize;
            this.history = new java.util.concurrent.ConcurrentLinkedDeque<>();
        }

        public void add(String original, String translated, boolean isOutgoing) {
            Entry entry = new Entry(original, translated, isOutgoing);

            while (history.size() >= maxSize) {
                history.pollFirst();
            }

            history.addLast(entry);
        }

        public List<Entry> getEntries() {
            return new ArrayList<>(history);
        }

        public int size() {
            return history.size();
        }

        public void clear() {
            history.clear();
        }

        public static class Entry {
            public final String original;
            public final String translated;
            public final boolean isOutgoing;

            public Entry(String original, String translated, boolean isOutgoing) {
                this.original = original;
                this.translated = translated;
                this.isOutgoing = isOutgoing;
            }
        }
    }
}
