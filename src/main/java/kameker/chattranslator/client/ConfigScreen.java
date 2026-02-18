package kameker.chattranslator.client;

import kameker.chattranslator.config.ModConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.ArrayList;

public class ConfigScreen extends Screen {
    private final Screen parent;

    // Компоненты настроек
    private Checkbox enableTranslationCheckbox;
    private Checkbox translateIncomingCheckbox;
    private Checkbox translateOutgoingCheckbox;
    private Checkbox autoDetectCheckbox;
    private Checkbox showOriginalCheckbox;
    private Checkbox showHintCheckbox;

    private EditBox sourceLanguageField;
    private EditBox targetLanguageField;
    private EditBox yandexApiKeyField;
    private EditBox yandexFolderIdField;
    private EditBox deeplApiKeyField;
    private EditBox blacklistField;
    private EditBox whitelistField;
    private EditBox cacheSizeField;

    private CycleButton<String> serviceButton;
    private CycleButton<String> whitelistModeButton;

    private Checkbox shiftModifierCheckbox;
    private Checkbox ctrlModifierCheckbox;
    private Checkbox altModifierCheckbox;
    private EditBox hotkeyField;

    private int scrollY = 0;
    private static final int SCROLL_SPEED = 20;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Chat Translator Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        scrollY = 0;
        createAllSettings();
    }

    private void createAllSettings() {
        int centerX = width / 2 - 125;
        int y = 10;

        // Заголовок
        addRenderableWidget(new StringWidget(
                centerX, y, 250, 20,
                Component.literal("Chat Translator Settings").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD),
                this.font
        ));
        y += 25;

        // === ОБЩИЕ НАСТРОЙКИ ===
        addSectionTitle("General Settings", centerX, y);
        y += 20;

        enableTranslationCheckbox = Checkbox.builder(
                Component.literal("Enable Translation"),
                this.font
        ).pos(centerX, y).selected(ModConfig.ENABLE_TRANSLATION.get()).build();
        addRenderableWidget(enableTranslationCheckbox);
        y += 22;

        translateIncomingCheckbox = Checkbox.builder(
                Component.literal("Translate Incoming Messages"),
                this.font
        ).pos(centerX, y).selected(ModConfig.TRANSLATE_INCOMING.get()).build();
        addRenderableWidget(translateIncomingCheckbox);
        y += 22;

        translateOutgoingCheckbox = Checkbox.builder(
                Component.literal("Translate Outgoing Messages"),
                this.font
        ).pos(centerX, y).selected(ModConfig.TRANSLATE_OUTGOING.get()).build();
        addRenderableWidget(translateOutgoingCheckbox);
        y += 22;

        showOriginalCheckbox = Checkbox.builder(
                Component.literal("Show Original in Tooltip"),
                this.font
        ).pos(centerX, y).selected(ModConfig.SHOW_ORIGINAL.get()).build();
        addRenderableWidget(showOriginalCheckbox);
        y += 22;

        showHintCheckbox = Checkbox.builder(
                Component.literal("Show Translation Hints"),
                this.font
        ).pos(centerX, y).selected(ModConfig.SHOW_TRANSLATION_HINT.get()).build();
        addRenderableWidget(showHintCheckbox);
        y += 22;

        addLabel("Cache Max Size:", centerX, y);
        cacheSizeField = new EditBox(this.font, centerX + 130, y, 60, 18,
                Component.literal("Cache size"));
        cacheSizeField.setValue(String.valueOf(ModConfig.CACHE_MAX_SIZE.get()));
        cacheSizeField.setFilter(s -> s.isEmpty() || s.matches("\\d{1,5}"));
        addRenderableWidget(cacheSizeField);
        y += 25;

        // === ЯЗЫКОВЫЕ НАСТРОЙКИ ===
        addSectionTitle("Language Settings", centerX, y);
        y += 20;

        autoDetectCheckbox = Checkbox.builder(
                Component.literal("Auto-detect Source Language"),
                this.font
        ).pos(centerX, y).selected(ModConfig.AUTO_DETECT_SOURCE.get()).build();
        addRenderableWidget(autoDetectCheckbox);
        y += 22;

        addLabel("Source Language:", centerX, y);
        sourceLanguageField = new EditBox(this.font, centerX + 130, y, 60, 18,
                Component.literal("Source lang"));
        sourceLanguageField.setValue(ModConfig.SOURCE_LANGUAGE.get());
        sourceLanguageField.setFilter(s -> s.isEmpty() || s.matches("[a-zA-Z]{0,2}"));
        addRenderableWidget(sourceLanguageField);
        y += 22;

        addLabel("Target Language:", centerX, y);
        targetLanguageField = new EditBox(this.font, centerX + 130, y, 60, 18,
                Component.literal("Target lang"));
        targetLanguageField.setValue(ModConfig.TARGET_LANGUAGE.get());
        targetLanguageField.setFilter(s -> s.isEmpty() || s.matches("[a-zA-Z]{0,2}"));
        addRenderableWidget(targetLanguageField);
        y += 25;

        // === СЕРВИСЫ ПЕРЕВОДА ===
        addSectionTitle("Translation Services", centerX, y);
        y += 20;

        addLabel("Service:", centerX, y);
        List<String> services = List.of("deepl", "yandex", "libre", "lingva", "mymemory");
        serviceButton = CycleButton.<String>builder(value ->
                        Component.literal(value.toUpperCase())
                ).withValues(services)
                .withInitialValue(ModConfig.TRANSLATION_SERVICE.get())
                .create(centerX + 130, y, 80, 18, Component.literal("Service"));
        addRenderableWidget(serviceButton);
        y += 22;

        addLabel("Yandex API Key:", centerX, y);
        yandexApiKeyField = new EditBox(this.font, centerX + 130, y, 100, 18,
                Component.literal("Yandex Key"));
        yandexApiKeyField.setValue(ModConfig.YANDEX_API_KEY.get());
        addRenderableWidget(yandexApiKeyField);
        y += 22;

        addLabel("Yandex Folder ID:", centerX, y);
        yandexFolderIdField = new EditBox(this.font, centerX + 130, y, 100, 18,
                Component.literal("Yandex ID"));
        yandexFolderIdField.setValue(ModConfig.YANDEX_FOLDER_ID.get());
        addRenderableWidget(yandexFolderIdField);
        y += 22;

        addLabel("DeepL API Key:", centerX, y);
        deeplApiKeyField = new EditBox(this.font, centerX + 130, y, 100, 18,
                Component.literal("DeepL Key"));
        deeplApiKeyField.setValue(ModConfig.DEEPL_API_KEY.get());
        addRenderableWidget(deeplApiKeyField);
        y += 25;

        // === ФИЛЬТРЫ ИГРОКОВ ===
        addSectionTitle("Player Filters", centerX, y);
        y += 20;

        addLabel("Mode:", centerX, y);
        whitelistModeButton = CycleButton.<String>builder(value ->
                        value.equals("true") ?
                            Component.literal("Whitelist").withStyle(ChatFormatting.GREEN) :
                            Component.literal("Blacklist").withStyle(ChatFormatting.RED)
                ).withValues(List.of("true", "false"))
                .withInitialValue(ModConfig.USE_WHITELIST_MODE.get() ? "true" : "false")
                .create(centerX + 130, y, 70, 18, Component.literal("Mode"));
        addRenderableWidget(whitelistModeButton);
        y += 22;

        addLabel("Blacklist (comma-sep):", centerX, y);
        blacklistField = new EditBox(this.font, centerX, y + 15, 250, 35,
                Component.literal("Blacklist"));
        blacklistField.setValue(String.join(", ", ModConfig.BLACKLISTED_PLAYERS.get()));
        blacklistField.setMaxLength(200);
        addRenderableWidget(blacklistField);
        y += 55;

        addLabel("Whitelist (comma-sep):", centerX, y);
        whitelistField = new EditBox(this.font, centerX, y + 15, 250, 35,
                Component.literal("Whitelist"));
        whitelistField.setValue(String.join(", ", ModConfig.WHITELISTED_PLAYERS.get()));
        whitelistField.setMaxLength(200);
        addRenderableWidget(whitelistField);
        y += 55;

        // === ГОРЯЧИЕ КЛАВИШИ ===
        addSectionTitle("Hotkeys", centerX, y);
        y += 20;

        addLabel("Toggle Key Code:", centerX, y);
        hotkeyField = new EditBox(this.font, centerX + 130, y, 50, 18,
                Component.literal("Hotkey"));
        hotkeyField.setValue(String.valueOf(ModConfig.HOTKEY_TOGGLE_TRANSLATION.get()));
        hotkeyField.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
        addRenderableWidget(hotkeyField);
        y += 22;

        addLabel("Modifiers:", centerX, y);
        y += 18;

        shiftModifierCheckbox = Checkbox.builder(
                Component.literal("Shift"),
                this.font
        ).pos(centerX, y).selected(ModConfig.HOTKEY_MODIFIER_SHIFT.get()).build();
        addRenderableWidget(shiftModifierCheckbox);

        ctrlModifierCheckbox = Checkbox.builder(
                Component.literal("Ctrl"),
                this.font
        ).pos(centerX + 80, y).selected(ModConfig.HOTKEY_MODIFIER_CTRL.get()).build();
        addRenderableWidget(ctrlModifierCheckbox);

        altModifierCheckbox = Checkbox.builder(
                Component.literal("Alt"),
                this.font
        ).pos(centerX + 160, y).selected(ModConfig.HOTKEY_MODIFIER_ALT.get()).build();
        addRenderableWidget(altModifierCheckbox);
        y += 25;

        addRenderableWidget(new StringWidget(
                centerX, y, 250, 30,
                Component.literal("Key codes: glfw.org/docs/3.3/group__keys.html")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                this.font
        ));
        y += 35;

        // Кнопки
        addRenderableWidget(Button.builder(
                Component.literal("Save"),
                button -> saveConfig()
        ).bounds(centerX, y, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                button -> onClose()
        ).bounds(centerX + 110, y, 100, 20).build());
    }

    private void addSectionTitle(String title, int x, int y) {
        addRenderableWidget(new StringWidget(
                x, y, 250, 15,
                Component.literal(title).withStyle(ChatFormatting.UNDERLINE, ChatFormatting.AQUA),
                this.font
        ));
    }

    private void addLabel(String text, int x, int y) {
        addRenderableWidget(new StringWidget(
                x, y + 3, 120, 15,
                Component.literal(text),
                this.font
        ));
    }

    private void saveConfig() {
        // Общие настройки
        ModConfig.ENABLE_TRANSLATION.set(enableTranslationCheckbox.selected());
        ModConfig.TRANSLATE_INCOMING.set(translateIncomingCheckbox.selected());
        ModConfig.TRANSLATE_OUTGOING.set(translateOutgoingCheckbox.selected());
        ModConfig.SHOW_ORIGINAL.set(showOriginalCheckbox.selected());
        ModConfig.SHOW_TRANSLATION_HINT.set(showHintCheckbox.selected());

        try {
            ModConfig.CACHE_MAX_SIZE.set(Integer.parseInt(cacheSizeField.getValue().isEmpty() ? "1000" : cacheSizeField.getValue()));
        } catch (NumberFormatException e) {
            ModConfig.CACHE_MAX_SIZE.set(1000);
        }

        // Языки
        ModConfig.AUTO_DETECT_SOURCE.set(autoDetectCheckbox.selected());
        ModConfig.SOURCE_LANGUAGE.set(sourceLanguageField.getValue().isEmpty() ? "en" : sourceLanguageField.getValue());
        ModConfig.TARGET_LANGUAGE.set(targetLanguageField.getValue().isEmpty() ? "ru" : targetLanguageField.getValue());

        // Сервисы
        ModConfig.TRANSLATION_SERVICE.set(serviceButton.getValue());
        ModConfig.YANDEX_API_KEY.set(yandexApiKeyField.getValue());
        ModConfig.YANDEX_FOLDER_ID.set(yandexFolderIdField.getValue());
        ModConfig.DEEPL_API_KEY.set(deeplApiKeyField.getValue());

        // Фильтры
        ModConfig.USE_WHITELIST_MODE.set(whitelistModeButton.getValue().equals("true"));
        ModConfig.BLACKLISTED_PLAYERS.set(parsePlayerList(blacklistField.getValue()));
        ModConfig.WHITELISTED_PLAYERS.set(parsePlayerList(whitelistField.getValue()));

        // Горячие клавиши
        try {
            ModConfig.HOTKEY_TOGGLE_TRANSLATION.set(
                    Integer.parseInt(hotkeyField.getValue().isEmpty() ? "0" : hotkeyField.getValue())
            );
        } catch (NumberFormatException e) {
            ModConfig.HOTKEY_TOGGLE_TRANSLATION.set(0);
        }
        ModConfig.HOTKEY_MODIFIER_SHIFT.set(shiftModifierCheckbox.selected());
        ModConfig.HOTKEY_MODIFIER_CTRL.set(ctrlModifierCheckbox.selected());
        ModConfig.HOTKEY_MODIFIER_ALT.set(altModifierCheckbox.selected());

        onClose();
    }

    private List<String> parsePlayerList(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String name : input.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Прокрутка для длинного списка настроек
        if (scrollY > 0) {
            scrollY = Math.min(0, scrollY + SCROLL_SPEED);
        } else {
            scrollY = Math.max(-200, scrollY - SCROLL_SPEED);
        }
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
