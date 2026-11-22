# APK HUB for Android

Современный автономный Android-менеджер установки и обновления приложений напрямую из публичных **GitHub Releases**.

## Основные возможности

- Добавление любых публичных GitHub-репозиториев (`owner/repo`, URL репозитория или раздела Releases).
- Автоматическое определение релизов по каналам **Stable / Beta / Test**.
- Интеллектуальный подбор совместимого `.apk` под `Build.SUPPORTED_ABIS`, `minSdk` и форм-фактор (смартфон, планшет, Android TV).
- Строгий фильтр форматов v1: `.xapk`, `.apks`, `.aab`, `.apkm` не выдаются за совместимые APK и явно помечаются как неподдерживаемые.
- Проверка целостности и безопасности перед установкой:
  - проверка точного размера файла (`Content-Length` / GitHub Asset `size`);
  - расчёт и сверка **SHA-256** (по release notes или файлам `.sha256` / `SHA256SUMS.txt`);
  - валидация ZIP-архива, `AndroidManifest.xml`, нативных библиотек `lib/<abi>/*.so`;
  - проверка `packageName`, `versionCode` (блокировка downgrade), `minSdkVersion` и совпадения **подписи сертификата** с установленной версией.
- Штатная установка через официальный `PackageInstaller.Session` API Android с точным получением результата установки через `InstallResultReceiver`.
- Фоновая проверка обновлений через `WorkManager` + уведомления.
- Автономная работа (кеширование релизов в Room), поддержка `ETag` (`If-None-Match` / HTTP `304`), отслеживание лимитов GitHub API и опциональный Personal Access Token.
- Экспорт и импорт резервной копии списка приложений и настроек в JSON (через Storage Access Framework).
- Самообновление APK HUB через настраиваемый GitHub-репозиторий.
- Material 3, Jetpack Compose, светлая, тёмная и графитовая темы, адаптивный интерфейс (NavigationBar / NavigationRail + сетка) и фокус-рамка для Android TV / пульта.

## Требования для сборки

- **JDK 17**
- **Android Studio Ladybug (2024.2)** или новее (с Android SDK Platform 35 и Build-Tools 35)
- **Gradle 8.9** (указан в `gradle/wrapper/gradle-wrapper.properties`)

## Инструкция по сборке

1. Клонируйте `https://github.com/georg1995105-hash/ApkHub.git` либо скачайте исходники через **Code → Download ZIP**.
2. Откройте корень проекта в **Android Studio**. Для CLI без бинарного `gradle-wrapper.jar` установите Gradle 8.9 или создайте wrapper командой `gradle wrapper --gradle-version 8.9`.
3. При необходимости укажите свой репозиторий для самообновления в `app/build.gradle.kts` (`SELF_UPDATE_REPO`).
4. Выполните синхронизацию Gradle (`Sync Project with Gradle Files`).
5. Запустите модульные тесты:
   ```bash
   gradle testDebugUnitTest
   ```
6. Соберите debug или release APK:
   ```bash
   gradle assembleDebug
   gradle assembleRelease
   ```
   Готовый APK появится в `app/build/outputs/apk/`.

## Что требует проверки в Android Studio и на реальном устройстве

22 ноября 2026 года в GitHub Actions успешно завершились `testDebugUnitTest`, `assembleDebug` и загрузка тестового APK: [Android CI run #44965023460](https://github.com/georg1995105-hash/ApkHub/actions/runs/44965023460). **Это подтверждает сборку и модульные тесты, но не испытания на устройстве.**

Перед пользовательским релизом ещё требуется проверить:
- Работу `PackageInstaller.Session` и `InstallResultReceiver` на реальных устройствах с Android 8–16 (включая сценарии подтверждения пользователем, отмены, конфликта подписи и обновления самого APK HUB).
- Навигацию пультом (D-Pad) на эмуляторе или реальном Android TV.
- Поведение `WorkManager` в Doze Mode и выдачу разрешения `POST_NOTIFICATIONS` на Android 13+.
- Конфигурацию `signingConfigs` для production release-сборки (`keystore`).


## Integration and release gates

The GitHub Actions workflow successfully built and uploaded a **debug-only, test-only** APK on 2026-11-22. It does not
publish a release or claim device validation. It uses a Gradle 8.9 toolchain
installation on the runner because the source archive has no `gradle-wrapper.jar`.

Before public use, verify installs on Android 8–16 and TV, including background
installer confirmation, cross-signature updates, uninstalled packages, process
death, canceled installation, and recovery. Release signing must use a stable
protected keystore; `assembleRelease` alone is not a deployable signed release.

The Android `QUERY_ALL_PACKAGES` permission needs review before any store
publication; GitHub-only distribution still needs a clear privacy rationale.
