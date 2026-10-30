# Thaumic Energistics

<img src="assets/branding/aop-logo.png" alt="Thaumic Energistics" width="256" height="256">

**AOP projects:** [Thaumcraft 4](https://github.com/Salossov/AOP-Thaumcraft-4) · [Compact](https://github.com/Salossov/AOPTC4-Compact) · [Thaumic Tinkerer](https://github.com/Salossov/AOPTC4-Thaumic-Tinkerer) · [AOP UI](https://github.com/Salossov/AOP-UI) · [Forbidden Magic](https://github.com/Salossov/AOPTC4-Forbidden-Magic) · [Thaumic Energistics](https://github.com/Salossov/AOPTC4-Thaumic-Energistics)

[English](README.md) · [Русский](README.ru.md)

Unofficial port of Nividica’s Thaumic Energistics **1.1.3.0 / AE2 RV3** to **Minecraft 1.21.1 / NeoForge**. Brings essentia cells, buses, terminals, providers, arcane crafting automation and golem backpacks to a modern AE2 network.

Install the gameplay JAR on client and server with **Java 21**, **NeoForge 21.1.166**, **AOP: Thaumcraft 4 1.1.0**, **Applied Energistics 2 19.0.24 or newer within 19.x** and **AOP UI 0.8.0**, plus their own dependencies. Optional ComputerCraft support uses a compatible CC: Tweaked. Recipe-viewer and HUD bridges live in AOP Compact. Source/API JARs do not belong in `mods/`.

The behavior reference is [the original AE2-RV3 branch](https://github.com/Nividica/ThaumicEnergistics/tree/AE2-RV3), pinned to release 1.1.3.0. Wireless equipment uses current AE2 access-point linking.

**1.0.0** · MIT · [Changelog](CHANGELOG.md)

NeoForge mod ID: **`thaumicenergistics_aop`**. Registry and resource namespace: **`thaumicenergistics`**. Existing item/block IDs and configuration filenames are retained.

## Settings

Open the mod through the Mods screen → Config, or select it in AOP UI. All existing settings have English and Russian labels and tooltips. Startup settings require a full restart; AOP UI shows pending changes. No additional balance settings are introduced.

## Build

Requires Java 21, Python 3.11+ and compatible AOP UI / Thaumcraft API and runtime JARs. Dependencies are never embedded in the gameplay JAR.

Build locally with `./gradlew.ps1 build sourcesJar` (PowerShell) or `./gradlew build sourcesJar` (POSIX), passing `-P<role>=<absolute path>` for the dependency roles listed below. The wrapper downloads the pinned Gradle distribution. CI builds our dependencies from their source repositories automatically.

## Continuous integration

In **Actions → Build and release → Run workflow**, enable **release** for an ordinary `v<version>` release; leave it unchecked for the single `nightly` prerelease. Blank **version** uses `mod_version`; an override must be `major.minor.patch`. Pull requests build without publishing. Existing ordinary tags are never overwritten; ordinary release notes list commits since the preceding release. Local `act` runs only validate publication plans and cannot publish.

Dependencies are built automatically from the AOP UI and Thaumcraft source repositories. Repository/ref defaults can be overridden with `AOPUI_REPOSITORY` / `AOPUI_REF`, `THAUMCRAFT_REPOSITORY` / `THAUMCRAFT_REF`, where applicable. `DEPENDENCY_TOKEN` needs Contents read access when a dependency repository is private; public source repositories need no additional token. No dependency archive URL or SHA-256 repository variables are required. Cached JARs are reused only when source revisions, content, toolchain and dependency checksums match. Dependencies are never published by this workflow.

The workflow generates and verifies its dependency manifest and SHA-256 automatically. Artifacts contain gameplay/sources JARs, `SHA256SUMS.txt` and build provenance.

For local `act` verification, export compatible source checkouts to `.dependencies/aopui`, `.dependencies/thaumcraft`, or set the corresponding `SUITE_*_ROOT` variables. Windows linked worktrees need ignored `.suite-source.json` inventories for exported dependency checkouts and `python .github/scripts/ci.py inventory` for the current component. Local publication is always a dry run.

## Artwork and credits

See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for authors and third-party terms. Third-party dependencies and the separate Thaumcraft runtime are not relicensed by this project.

Required dependency roles: `thaumcraftApiJar`, `thaumcraftRuntimeJar`, `aopUiJar`.

AE2 and Curios are resolved from the repositories declared in Gradle; their versions are pinned in `gradle.properties`.

- Nividica: original Thaumic Energistics; CyanideX: original textures.
- [Salossov](https://github.com/Salossov): port and maintenance.
- NeoForge, AOP: Thaumcraft 4, AOP UI and the integrated mod projects: platform and APIs.
