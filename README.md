# Thaumic Energistics

<img src="assets/branding/aop-logo.png" alt="Thaumic Energistics" width="256" height="256">

**AOP projects:** [Thaumcraft 4](https://github.com/Salossov/AOP-Thaumcraft-4) · [Compact](https://github.com/Salossov/AOPTC4-Compact) · [Thaumic Tinkerer](https://github.com/Salossov/AOPTC4-Thaumic-Tinkerer) · [AOP UI](https://github.com/Salossov/AOP-UI) · [Forbidden Magic](https://github.com/Salossov/AOPTC4-Forbidden-Magic) · [Thaumic Energistics](https://github.com/Salossov/AOPTC4-Thaumic-Energistics)

[English](README.md) · [Русский](README.ru.md)

Unofficial port of Nividica’s Thaumic Energistics **1.1.3.0 / AE2 RV3** to **Minecraft 1.21.1 / NeoForge**. Brings essentia cells, buses, terminals, providers, arcane crafting automation and golem backpacks to a modern AE2 network.

Install the gameplay JAR on client and server with **Java 21**, **NeoForge 21.1.252**, **AOP: Thaumcraft 4 1.0.0**, **Applied Energistics 2 19.2.18** and **AOP UI 0.7.0**, plus their own dependencies. Optional ComputerCraft support uses a compatible CC: Tweaked. Recipe-viewer and HUD bridges live in AOP Compact. Source/API JARs do not belong in `mods/`.

The behavior reference is [the original AE2-RV3 branch](https://github.com/Nividica/ThaumicEnergistics/tree/AE2-RV3), pinned to release 1.1.3.0. Wireless equipment uses current AE2 access-point linking.

**1.1.3.0-port.1** · MIT

## Settings

Open the mod through the Mods screen → Config, or select it in AOP UI. All existing settings have English and Russian labels and tooltips. Startup settings require a full restart; AOP UI shows pending changes. No additional balance settings are introduced.

## Build

Requires Java 21, Python 3.11+ and the pinned dependency bundle. The API and runtime JARs are dependencies, never embedded in this mod.

Set `AOP_DEPENDENCIES_URL` to the HTTPS URL of your dependency ZIP and `AOP_DEPENDENCIES_SHA256` to its exact SHA-256. Then run from the repository root:

```powershell
python .github/scripts/ci.py prepare
python .github/scripts/ci.py build
```

This runs the Gradle wrapper directly through Java, including `clean build sourcesJar` and the repository's checks. Verified gameplay and sources JARs, a build log and a source/dependency hash manifest go to `dist/`. A changed source, dependency, Java runtime or damaged cached JAR requires a rebuild.

For a manual local build, `./gradlew.ps1 build sourcesJar` (PowerShell) or `./gradlew build sourcesJar` (POSIX) accepts explicit `-P<role>=<absolute path>` properties listed below. The wrapper downloads the pinned Gradle distribution. A local checkout layout is only a convenience; CI uses explicit paths.

## CI

`.github/workflows/build.yml` runs checks and Java 21 builds on pushes to `main`, pull requests and manual dispatch. Configure repository variables `AOP_DEPENDENCIES_URL` and `AOP_DEPENDENCIES_SHA256`, or supply the manual dispatch overrides. The workflow uploads build artifacts; it does not publish releases or push commits.

The ZIP contains `manifest.json` and only the JARs it lists:

```json
{
  "format": 1,
  "artifacts": {
    "thaumcraftApiJar": {
      "file": "jars/thaumcraft-api-1.0.0.jar",
      "sha256": "<64 lowercase hexadecimal characters>"
    }
  }
}
```

Add every required role below to `artifacts`. Each role has a unique JAR path and SHA-256; runtime entries may also set `mod_id` to validate their NeoForge mod ID. Include files without separate ZIP directory entries. The entire ZIP checksum is checked before extraction and each JAR is checked afterwards. Host only dependencies you are authorized to distribute; a private archive is sufficient. No machine-specific URL or dependency archive is committed.

For local workflow verification use `act workflow_dispatch -j build` with the two dependency variables in an environment file. A read-only local HTTP fixture is allowed only under `ACT=true`; GitHub CI requires HTTPS. Windows linked worktrees also need `python .github/scripts/ci.py inventory` before `act`, because their `.git` points to a host path. This exports an ignored tracked-file inventory; CI hashes the actual file contents.

## Artwork and credits

The editable vector logo is [assets/branding/logo.svg](assets/branding/logo.svg). Its transparent 512 × 512 and 64 × 64 PNG exports are packaged for NeoForge and AOP UI, which load PNG textures. The SVG remains the source of the artwork.

See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for authors and third-party terms. Third-party dependencies and the separate Thaumcraft runtime are not relicensed by this project.

Required dependency roles: `thaumcraftApiJar`, `thaumcraftRuntimeJar`, `aopUiJar`.

AE2 and Curios are resolved from the repositories declared in Gradle; their versions are pinned in `gradle.properties`.
