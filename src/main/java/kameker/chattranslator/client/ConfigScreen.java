package kameker.chattranslator.client;

import kameker.chattranslator.config.ModConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.List;

public class ConfigScreen extends Screen {
    private final Screen parent;

    // Компоненты настроек
    private Checkbox enableTranslationCheckbox;
    private Checkbox translateIncomingCheckbox;
    private Checkbox translateOutgoingCheckbox;
    private Checkbox autoDetectCheckbox;
    private Checkbox showOriginalCheckbox;

    private EditBox sourceLanguageField;
    private EditBox targetLanguageField;

    private CycleButton<String> serviceButton;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Chat Translator Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int y = 30;
        int centerX = width / 2 - 125;

        // Заголовок
        addRenderableWidget(new StringWidget(
                width / 2 - 100, y, 200, 20,
                Component.literal("Chat Translator Settings").withStyle(ChatFormatting.BOLD),
                this.font
        ));
        y += 25;

        // Основные настройки
        addSectionTitle("General Settings", y);
        y += 15;

        enableTranslationCheckbox = Checkbox.builder(
                Component.literal("Enable Translation"),
                this.font
        ).pos(centerX, y).selected(ModConfig.ENABLE_TRANSLATION.get()).build();
        addRenderableWidget(enableTranslationCheckbox);
        y += 25;

        translateIncomingCheckbox = Checkbox.builder(
                Component.literal("Translate Incoming Messages"),
                this.font
        ).pos(centerX, y).selected(ModConfig.TRANSLATE_INCOMING.get()).build();
        addRenderableWidget(translateIncomingCheckbox);
        y += 25;

        translateOutgoingCheckbox = Checkbox.builder(
                Component.literal("Translate Outgoing Messages"),
                this.font
        ).pos(centerX, y).selected(ModConfig.TRANSLATE_OUTGOING.get()).build();
        addRenderableWidget(translateOutgoingCheckbox);
        y += 30;

        // Настройки языков
        addSectionTitle("Language Settings", y);
        y += 15;

        autoDetectCheckbox = Checkbox.builder(
                Component.literal("Auto-detect Source Language"),
                this.font
        ).pos(centerX, y).selected(ModConfig.AUTO_DETECT_SOURCE.get()).build();
        addRenderableWidget(autoDetectCheckbox);
        y += 25;

        // Поле для исходного языка
        addLabel("Source Language:", centerX, y);
        sourceLanguageField = new EditBox(this.font, centerX + 100, y, 80, 20,
                Component.literal("Source language code"));
        sourceLanguageField.setValue(ModConfig.SOURCE_LANGUAGE.get());
        sourceLanguageField.setEditable(!ModConfig.AUTO_DETECT_SOURCE.get());
        addRenderableWidget(sourceLanguageField);
        y += 25;

        // Поле для целевого языка
        addLabel("Target Language:", centerX, y);
        targetLanguageField = new EditBox(this.font, centerX + 100, y, 80, 20,
                Component.literal("Target language code"));
        targetLanguageField.setValue(ModConfig.TARGET_LANGUAGE.get());
        addRenderableWidget(targetLanguageField);
        y += 30;

        // Дополнительные настройки
        addSectionTitle("Display Settings", y);
        y += 15;

        showOriginalCheckbox = Checkbox.builder(
                Component.literal("Show Original Message"),
                this.font
        ).pos(centerX, y).selected(ModConfig.SHOW_ORIGINAL.get()).build();
        addRenderableWidget(showOriginalCheckbox);
        y += 25;

        // Выбор сервиса - ИСПРАВЛЕНО!
        addLabel("Translation Service:", centerX, y);

        // Создаем список значений
        List<String> services = List.of("mymemory", "libretranslate", "lingva", "yandex");

        serviceButton = CycleButton.<String>builder(value ->
                        Component.literal(value)
                ).withValues(services)
                .withInitialValue(ModConfig.TRANSLATION_SERVICE.get())
                .create(centerX + 100, y, 80, 20, Component.literal("Service"));

        addRenderableWidget(serviceButton);
        y += 40;

        // Кнопки сохранения
        addRenderableWidget(Button.builder(
                Component.literal("Save"),
                button -> saveConfig()
        ).bounds(centerX, y, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                button -> onClose()
        ).bounds(centerX + 110, y, 100, 20).build());

        // Подсказка по кодам языков
        y += 30;
        addRenderableWidget(new StringWidget(
                centerX, y, 250, 40,
                Component.literal("Language codes: en (English), ru (Russian), da (Danish), es, fr, de, it, zh, ja, ko")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                this.font
        ));
    }

    private void addSectionTitle(String title, int y) {
        addRenderableWidget(new StringWidget(
                width / 2 - 125, y, 250, 12,
                Component.literal(title).withStyle(ChatFormatting.UNDERLINE),
                this.font
        ));
    }

    private void addLabel(String text, int x, int y) {
        addRenderableWidget(new StringWidget(
                x, y + 4, 100, 12,
                Component.literal(text),
                this.font
        ));
    }

    private void saveConfig() {
        ModConfig.ENABLE_TRANSLATION.set(enableTranslationCheckbox.selected());
        ModConfig.TRANSLATE_INCOMING.set(translateIncomingCheckbox.selected());
        ModConfig.TRANSLATE_OUTGOING.set(translateOutgoingCheckbox.selected());
        ModConfig.AUTO_DETECT_SOURCE.set(autoDetectCheckbox.selected());
        ModConfig.SHOW_ORIGINAL.set(showOriginalCheckbox.selected());

        ModConfig.SOURCE_LANGUAGE.set(sourceLanguageField.getValue());
        ModConfig.TARGET_LANGUAGE.set(targetLanguageField.getValue());
        ModConfig.TRANSLATION_SERVICE.set(serviceButton.getValue());

        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}