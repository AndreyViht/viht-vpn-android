# Viht VPN Android — План реализации и трекер этапов

Данный документ является единым оперативным планом создания мобильного клиента **Viht VPN для Android** на базе ядра sing-box 1.13+ (`libbox.aar`) и Jetpack Compose, полностью согласованного по API, функционалу и дизайну с **Viht VPN Desktop (Windows)** и экосистемой сервиса **Viht VPN**.

---

## Статус выполнения

- [x] **Этап 1: Архитектура, базовые компоненты и аудит ядра**
  - [x] Аудит базового проекта (`pingwin`) — архитектура VpnService, интеграция `libbox.aar`, Gradle dependencies.
  - [x] Загрузка и верификация полного `libbox.aar` (92.9 MB, SHA-256: `3A706062E1B3E804CA7F7ABBDEF523A05C4AD7C125AB1496E2B0A52A62C360A5`).
  - [x] Проверка токена и интеграции с GitHub API (`AndreyViht`).
  - [x] Создание CI/CD воркфлоу сборки `.github/workflows/android-release.yml` для автоматической генерации Release APK.
  - [x] Обновление воркфлоу тестирования `.github/workflows/android-ci.yml`.

- [x] **Этап 2: Ребрендинг, манифест и базовая конфигурация**
  - [x] Обновление `app/build.gradle.kts`:
    - `applicationId = "ru.anviht.vpn"`
    - `versionCode = 1`
    - `versionName = "1.0.0"`
    - Добавление `androidx-compose-material-icons-extended`
  - [x] Настройка `AndroidManifest.xml`:
    - Название приложения `@string/app_name` ("Viht VPN")
    - Регистрация Deep-link схем: `vihtvpn://auth`, `vihtvpn://connect`, `https://anviht.ru/auth`
    - Разрешения на сеть, уведомления, foreground service и автозапуск
  - [x] Обновление строк и локализации `values/strings.xml` и `values-ru/strings.xml` (полная поддержка RU / EN).

- [x] **Этап 3: Cyberpunk / Neon Dark Дизайн система (Jetpack Compose)**
  - [x] Цветовая палитра Viht Theme в `Color.kt`:
    - Фон: `#07090E`, поверхность: `#0E1320`, карточки: `#12192A`
    - Неоновый циан: `#00D2FF`, электрический фиолетовый: `#7000FF`
    - Успех / Активен: `#00FF88`, Ошибка / Отключен: `#FF3344`
  - [x] Тёмная тема в `Theme.kt` (`VihtColorScheme`, `VihtTheme`)
  - [x] Фирменные неоновые Compose-компоненты в `VihtComponents.kt`:
    - `NeonPowerButton` (анимированная неоновая кнопка питания с пульсирующим ореолом)
    - `VihtGlassCard` (стеклянная карточка с тонкой неоновой окантовкой)
    - `VihtTopBar` (верхняя фирменная панель с бейджем статуса подписки)
    - `VihtBottomNavBar` (стильный нижний бар навигации: Главная, Серверы, Кабинет, Настройки)

- [x] **Этап 4: Сетевой слой и интеграция с Viht Cabinet API**
  - [x] Модели данных `VihtModels.kt` (`VihtServer`, `VihtUserProfile`, `VihtDevice`, `VihtAuthType`)
  - [x] Сетевой клиент `VihtApiClient.kt`:
    - Базовый URL: `https://anviht.ru/cabinet-api`
    - Отправка проверочных кодов в бота Telegram `@vpnvihtbot`
    - Загрузка профиля и подписки (`action=profile`)
    - Загрузка списка активных устройств (`action=active-devices`)
    - Отвязка устройств (`action=device-action`)
    - Замер задержки (ping) по сокетам
    - Эталонный каталог серверов Viht по умолчанию
  - [x] Хранилище настроек `VihtPreferences.kt`:
    - Генерация и сохранение стабильного уникального Android HWID
    - Хранение токена авторизации, Telegram ID, выбранного сервера, флагов обхода РФ и автозапуска

- [x] **Этап 5: Логика VPN и sing-box Core (AutoVlessVpnService)**
  - [x] Адаптация маршрутизации в `SingBoxRoutingConfigBuilder.kt`:
    - Блокировка QUIC (UDP 443 -> reject) для моментального переключения на HTTP/2 без подвисаний
    - Режим обхода российских сайтов (`geosite:ru`, доменные суффиксы `.ru`, `.рф`, `.su`, банки, госуслуги -> direct)
    - Раздельное туннелирование приложений (Split Tunneling)
  - [x] Адаптация автозапуска в `AutomationBootReceiver.kt` с учетом настройки пользователя
  - [x] Интеграция VpnService с VLESS-профилями серверов

- [x] **Этап 6: Реализация экранов приложения (Compose UI)**
  - [x] `VihtHomeScreen.kt` — большая неоновая кнопка, статус защищенности, сессионный таймер, карточка активного сервера, тумблер обхода РФ
  - [x] `VihtServersScreen.kt` — список серверов (Германия, Нидерланды, Стокгольм, Россия Ютуб, LTE), замер пинга, бейджи, выбор в один тап
  - [x] `VihtCabinetScreen.kt` — статус подписки, оставшиеся дни с полосой прогресса, кнопка продления, список активных устройств с информацией и кнопкой отвязки
  - [x] `VihtAuthScreen.kt` — вход по Telegram ID с кодом в бота, по ссылке/токену подписки (`sub:...`), или через VK / Яндекс
  - [x] `VihtSettingsScreen.kt` — обход сайтов РФ, раздельное туннелирование (выбор приложений), автозапуск, Kill Switch, выбор языка, логи
  - [x] `MainActivity.kt` — главное окно, объединяющее навигацию по табам, обработку deep link, фоновую синхронизацию с API и управление VPN

- [x] **Этап 8: Исправление авторизации и релиз v1.0.2**
  - [x] Исправлен базовый endpoint в `VihtApiClient.kt`: изменен с `https://anviht.ru/cabinet-api` (где Nginx отдавал HTML SPA с ошибкой `<!doctype...`) на `https://anviht.ru/api/cabinet-api` (реальная Edge Function Supabase).
  - [x] Исправлена отправка проверочных кодов в бота Telegram `@vpnvihtbot`: проверено сквозным тестом для ID `8613298149`.
  - [x] Исправлен формат заголовков `Authorization: Bearer sub:...` для бесшовной валидации токенов подписки и сессий.
  - [x] Добавлена кнопка «Вставить из буфера» в окне ожидания браузерной авторизации (Яндекс ID и VK ID).
  - [x] Улучшен парсинг ссылок подписки в табе «Ключ» (поддержка `anviht.ru/sub/...`, `subscription-link/...` и прямых ссылок `vless://`).
  - [x] Публикация официального релиза GitHub Release `v1.0.2`:
    - Ссылка на скачивание: https://github.com/AndreyViht/viht-vpn-android/releases/download/v1.0.2/VihtVPN.apk
    - Страница релиза: https://github.com/AndreyViht/viht-vpn-android/releases/tag/v1.0.2


