# Thaumic Energistics

<img src="assets/branding/aop-logo.png" alt="Thaumic Energistics" width="256" height="256">

**Проекты AOP:** [Thaumcraft 4](https://github.com/Salossov/AOP-Thaumcraft-4) · [Compact](https://github.com/Salossov/AOPTC4-Compact) · [Thaumic Tinkerer](https://github.com/Salossov/AOPTC4-Thaumic-Tinkerer) · [AOP UI](https://github.com/Salossov/AOP-UI) · [Forbidden Magic](https://github.com/Salossov/AOPTC4-Forbidden-Magic) · [Thaumic Energistics](https://github.com/Salossov/AOPTC4-Thaumic-Energistics)

[English](README.md) · [Русский](README.ru.md)

Неофициальный порт Thaumic Energistics **1.1.3.0 / AE2 RV3** от Nividica на **Minecraft 1.21.1 / NeoForge**. Добавляет в современную сеть AE2 ячейки эссенции, шины, терминалы, поставщики, автоматизацию арканного крафта и рюкзаки големов.

Установите игровой JAR на клиент и сервер вместе с **Java 21**, **NeoForge 21.1.166**, **AOP: Thaumcraft 4 1.1.0**, **Applied Energistics 2 от 19.0.24 в пределах 19.x**, **AOP UI 0.8.0** и их зависимостями. Для ComputerCraft нужна совместимая CC: Tweaked. Интеграции подсказок и просмотрщиков рецептов находятся в AOP Compact. JAR исходников и API в `mods/` не устанавливаются.

Эталон поведения: [исходная ветка AE2-RV3](https://github.com/Nividica/ThaumicEnergistics/tree/AE2-RV3), релиз 1.1.3.0. Беспроводные устройства используют современную привязку к точке доступа AE2.

**1.0.0** · MIT · [История изменений](CHANGELOG.ru.md)

Mod ID NeoForge: **`thaumicenergistics_aop`**. Namespace реестров и ресурсов: **`thaumicenergistics`**. Существующие ID предметов/блоков и имена конфигов сохранены.

## Настройки

Откройте «Моды» → мод → «Настройки» либо выберите мод в AOP UI. Для всех существующих параметров добавлены русские и английские названия и подсказки. Параметры запуска требуют полного перезапуска; AOP UI показывает ожидающие применения изменения. Новые настройки баланса не добавлены.

## Сборка

Нужны Java 21, Python 3.11+, совместимые JAR AOP UI и Thaumcraft API/ядра. Зависимости не встраиваются в игровой JAR.

Локальная сборка: `./gradlew.ps1 build sourcesJar` (PowerShell) либо `./gradlew build sourcesJar` (POSIX), с параметрами `-P<роль>=<абсолютный путь>` из списка ниже. Wrapper загружает закреплённую версию Gradle. В CI наши зависимости собираются автоматически из исходников.

## Непрерывная интеграция

В **Actions → Build and release → Run workflow** включите **release** для обычного релиза `v<версия>`; без флажка обновляется единственный prerelease `nightly`. Пустая **version** использует `mod_version`, переопределение задаётся как `major.minor.patch`. Pull request собирается без публикации. Существующие обычные теги не перезаписываются; примечания к релизу содержат коммиты с предыдущего релиза. Локальный `act` проверяет только план публикации и не может публиковать.

Зависимости собираются автоматически из исходников AOP UI и Thaumcraft. Репозитории и ветки можно переопределить переменными `AOPUI_REPOSITORY` / `AOPUI_REF`, `THAUMCRAFT_REPOSITORY` / `THAUMCRAFT_REF` по необходимости. Для закрытых репозиториев нужен `DEPENDENCY_TOKEN` с Contents read; для публичных отдельный токен не нужен. URL и SHA-256 архива в настройках репозитория не требуются. Кеш проверяется по ревизиям, содержимому исходников, окружению сборки и хешам зависимостей. Workflow публикует только текущий мод.

Манифест зависимостей и SHA-256 создаются и проверяются автоматически. Результат включает игровые JAR, исходники, `SHA256SUMS.txt` и сведения о сборке.

Для локального `act` подготовьте исходники в `.dependencies/aopui`, `.dependencies/thaumcraft` либо укажите `SUITE_*_ROOT`. Для экспортированных checkout нужны игнорируемые `.suite-source.json`, а для текущего мода используйте `python .github/scripts/ci.py inventory`. Локально публикация всегда проверяется через dry run.

## Графика и авторство

Авторы и условия сторонних материалов: [LICENSE](LICENSE), [NOTICE.md](NOTICE.md). Этот проект не меняет лицензии зависимостей и отдельного ядра Thaumcraft.

Обязательные роли зависимостей: `thaumcraftApiJar`, `thaumcraftRuntimeJar`, `aopUiJar`.

AE2 и Curios загружаются из Maven-репозиториев, указанных в Gradle; версии закреплены в `gradle.properties`.

- Nividica: оригинальный Thaumic Energistics; CyanideX: исходные текстуры.
- [Salossov](https://github.com/Salossov): порт и сопровождение.
- NeoForge, AOP: Thaumcraft 4, AOP UI и проекты интегрируемых модов: платформа и API.
