# Chat Translator Mod

Мод для Minecraft NeoForge, обеспечивающий автоматический перевод чата между игроками на разные языки.

## 🚀 Возможности

### Основные функции
- ✅ **Перевод входящих сообщений** — переводит сообщения от других игроков
- ✅ **Перевод исходящих сообщений** — переводит ваши сообщения перед отправкой
- ✅ **Автоопределение языка** — автоматически определяет язык сообщения
- ✅ **Несколько сервисов перевода** — DeepL, Yandex, LibreTranslate, Lingva, MyMemory
- ✅ **Fallback система** — если один сервис не работает, используется другой
- ✅ **Кэширование переводов** — сохраняет переводы в памяти и на диске
- ✅ **Rate Limiting** — защита от превышения лимитов API

### Фильтры
- 📋 **Чёрный список игроков** — не переводить сообщения от определённых игроков
- 📋 **Белый список игроков** — переводить только сообщения от выбранных игроков
- 📋 **Фильтр системных сообщений** — автоматический пропуск служебных сообщений

### Удобство использования
- ⌨️ **Горячие клавиши** — быстрое включение/выключение перевода
- 📜 **История переводов** — просмотр последних 50 переводов
- 🎨 **Цветная индикация** — визуальное различие входящих/исходящих переводов
- 💾 **Сохранение кэша** — переводы сохраняются между сессиями

## 📦 Установка

1. Установите **Minecraft NeoForge** соответствующей версии
2. Скачайте мод `chattranslator.jar`
3. Поместите файл в папку `mods` вашего Minecraft
4. Запустите игру

## ⚙️ Настройка

### Через GUI в игре
Нажмите `ESC` → `Options` → `Mods` → `Chat Translator` → `Config`

Или используйте команду в чате (если есть поддержка Mod Menu)

### Через файл конфигурации
Файл: `.minecraft/config/chattranslator.toml`

### Основные настройки

#### General Settings
```toml
enableTranslation = true          # Включить перевод
translateIncoming = true          # Переводить входящие
translateOutgoing = false         # Переводить исходящие
messageTimeout = 3000             # Таймаут дубликатов (мс)
```

#### Language Settings
```toml
autoDetectSource = true           # Автоопределение языка
sourceLanguage = "en"             # Исходный язык (если авто отключено)
targetLanguage = "ru"             # Целевой язык перевода
```

#### Translation Services
```toml
translationService = "deepl"      # Предпочтительный сервис
yandexApiKey = ""                 # Yandex API ключ (опционально)
yandexFolderId = ""               # Yandex Folder ID (опционально)
deeplApiKey = ""                  # DeepL API ключ (опционально)
```

#### Player Filters
```toml
useWhitelistMode = false          # Режим белого списка
blacklistedPlayers = ["Server", "Admin"]
whitelistedPlayers = []
```

#### Hotkeys
```toml
hotkeyToggleTranslation = 0       # Код клавиши (0 = отключено)
hotkeyModifierShift = false       # Требовать Shift
hotkeyModifierCtrl = true         # Требовать Ctrl
hotkeyModifierAlt = false         # Требовать Alt
```

**Коды клавиш:** https://www.glfw.org/docs/3.3/group__keys.html

## 💬 Команды в чате

| Команда | Описание |
|---------|----------|
| `!translate history` или `!th` | Показать историю переводов |
| `!translate clear` или `!tc` | Очистить историю переводов |
| `!translate stats` или `!ts` | Показать статистику кэша |

## 🔑 API ключи

### DeepL (рекомендуется)
1. Зарегистрируйтесь на https://www.deepl.com/pro-api
2. Получите API ключ в личном кабинете
3. Укажите ключ в настройках мода

**Преимущества:**
- ✅ Высокое качество перевода
- ✅ Поддержка 30+ языков
- ✅ Бесплатный тариф: 500,000 символов/месяц

### Yandex Translate
1. Создайте аккаунт в Яндекс.Облако
2. Создайте платёжный аккаунт (даётся грант)
3. Создайте каталог и получите Folder ID
4. Создайте API ключ

**Преимущества:**
- ✅ Хорошее качество для русских текстов
- ✅ Бесплатный грант при регистрации

### Бесплатные сервисы (не требуют ключей)
- **MyMemory** — базовый перевод, есть лимиты
- **LibreTranslate** — открытый, но может быть медленным
- **Lingva** — альтернативный вариант

## 🏗️ Сборка из исходников

### Требования
- Java 21+
- Gradle 8.x

### Команды
```bash
# Клонирование репозитория
git clone https://github.com/yourusername/chattranslator.git
cd chattranslator

# Сборка
./gradlew build

# Запуск клиента для тестов
./gradlew runClient

# Запуск тестов
./gradlew test
```

## 🧪 Тесты

Мод включает unit-тесты для основных компонентов:

- `TranslationManagerTest` — тесты перевода
- `TranslationCacheTest` — тесты LRU кэша
- `RateLimiterTest` — тесты ограничения частоты
- `ModConfigTest` — тесты конфигурации

Запуск тестов:
```bash
./gradlew test
```

## 📊 Структура проекта

```
chattranslator/
├── src/main/java/kameker/chattranslator/
│   ├── ChatHandler.java          # Обработка сообщений чата
│   ├── Chattranslator.java       # Основной класс мода
│   ├── client/
│   │   └── ConfigScreen.java     # GUI настроек
│   ├── config/
│   │   └── ModConfig.java        # Конфигурация
│   └── translation/
│       └── TranslationManager.java # Менеджер переводов
├── src/test/java/kameker/chattranslator/
│   └── translation/              # Unit-тесты
└── build.gradle                  # Сборка Gradle
```

## 🔧 Технические детали

### LRU Кэш
- Максимальный размер: 1000 записей (настраивается)
- Автоматическое сохранение на диск каждые 5 минут
- При переполнении удаляются наименее используемые записи
- Подсчёт hit rate для мониторинга эффективности

### Rate Limiting
- DeepL: 20 запросов/минуту
- Остальные: 50 запросов/минуту
- Скользящее окно
- Безопасная многопоточная реализация

### Fallback система
При ошибке перевода сервисы перебираются в порядке:
1. DeepL (если есть ключ)
2. Yandex (если есть ключи)
3. LibreTranslate
4. Lingva
5. MyMemory

## 🐛 Решение проблем

### Перевод не работает
1. Проверьте, что `enableTranslation = true`
2. Убедитесь, что выбран правильный целевой язык
3. Проверьте подключение к интернету

### Ошибки API
1. Проверьте логи в `logs/latest.log`
2. Убедитесь, что API ключи указаны верно
3. Попробуйте сменить сервис перевода

### Медленный перевод
1. Включите кэширование
2. Используйте DeepL или Yandex (быстрее бесплатных)
3. Проверьте скорость интернета

## 📝 Changelog

### Версия 2.0
- ✨ Добавлен DeepL API
- ✨ Добавлены чёрный/белый списки игроков
- ✨ Добавлены горячие клавиши
- ✨ Добавлена история переводов
- ✨ Добавлено кэширование на диск
- ✨ Улучшена обработка ошибок (fallback)
- ✨ Добавлен rate limiting
- ✨ Разбиение длинных сообщений
- ✨ Улучшенный GUI с вкладками
- 🐛 Исправлен MESSAGE_TIMEOUT
- 🐛 Улучшено определение языка

### Версия 1.0
- Базовый перевод через MyMemory
- Автоопределение языка
- Фильтр системных сообщений

## 📄 Лицензия

MIT License

## 👥 Авторы

- Kameker — основной разработчик

## 🔗 Ссылки

- [GitHub Repository](https://github.com/yourusername/chattranslator)
- [CurseForge](https://curseforge.com/minecraft/mc-mods/chattranslator)
- [Modrinth](https://modrinth.com/mod/chattranslator)

## 💰 Поддержка

Если мод вам нравится, рассмотрите возможность поддержки:
- [GitHub Sponsors](https://github.com/sponsors/yourusername)
- [Ko-fi](https://ko-fi.com/yourusername)
- [PayPal](https://paypal.me/yourusername)
