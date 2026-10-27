# Viht VPN для Android

Android-клиент Viht VPN с общим интерфейсом Liquid Glass из Windows-клиента. Android 8.0+, актуальный Android System WebView (103+). Универсальный APK: arm64-v8a, armeabi-v7a, x86, x86_64.

## Тестовая сборка

GitHub Actions → Build Viht VPN APK → Run workflow, нужная ветка. Результат — artifact `VihtVPN-Android-test`, APK `VihtVPN-1.4.0-test-universal.apk`. Приложение устанавливается отдельно как `Viht VPN Test`, пакет `ru.anviht.vpn.test`. Workflow не публикует стабильный релиз. Тестовая подпись кэшируется Actions; для стабильной публикации нужен постоянный release-keystore.

Интерфейс: `cd web`, `npm ci`, `npm test`, `npm run build`. Android: JDK/Gradle из настроек проекта, `./gradlew verifyLibbox testDebugUnitTest lintDebug assembleDebug`. Ядро скачивается через `node tools/fetch-libbox.mjs`, проверяется размер и SHA-256.

## Работа

Вход через Яндекс ID или VK ID в браузере. Ключи загружаются из фактической подписки с HWID телефона. Подключение использует Android VpnService и libbox; Android запрашивает системное разрешение. Статус подключения появляется после проверки HTTPS через выбранный прокси. TCP-пинг — время установки соединения до адреса ключа, он не подтверждает доступность конкретного сайта.

Физическую проверку VPN, Wi-Fi/LTE, фонового режима и блокировки экрана нужно выполнять на телефоне. Закрытие интерфейса оставляет активный foreground VPN; отключение и отзыв разрешения освобождают TUN. Always-on/режим блокировки Android пока не заявлены и отключены в metadata службы.

## Лицензия и происхождение

Нативная часть основана на [pingwin](https://github.com/scripchenko/pingwin) и sing-box. Сохранены GPL-3.0-or-later, исходники и [описание происхождения libbox](LIBBOX_PROVENANCE.md). [Исходный README upstream](docs/UPSTREAM_README.md). Подробности восстановления: [ANDROID_REPAIR.md](docs/ANDROID_REPAIR.md).
