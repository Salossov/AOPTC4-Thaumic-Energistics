# Thaumic Energistics port

- Work independently without subagents unless the user explicitly requests them.
- Keep this a standalone project. Do not add it to the parent Thaumcraft build.
- The only gameplay reference is Thaumic Energistics 1.1.3.0, release SHA-1 a6240b09a0680f3e3246a6d0fe17650299533a84. Reference sources and decompilation live in ../source_code and ../decompiled, outside this project.
- Target Minecraft 1.21.1, NeoForge 21.1.252, Java 21, AE2 19.2.18 and the local Thaumcraft 1.0.0 public API.
- Keep original numeric values and observable mechanics, including the 16348-byte 16K cell, 2 essentia/byte and 12-type limit. Modernize removed APIs and unavailable ingredients; document replacements.
- Essentia is a custom AEKeyType with indivisible units, not a fluid.
- Ask before product/architecture changes. Ask before implementing any additional public Thaumcraft API contract. Never bypass a missing contract through core implementation imports.
- JEI/EMI integration extends the existing thaumcraft_viewers addon with a reusable menu bridge; do not copy its recipe categories. Also support optional Jade, WTHIT, CC:Tweaked and the in-progress local Tinkerer.
- No project-wide scans without permission; narrow every search. Use PowerShell, never cmd.exe or .bat. The user explicitly authorized a Git repository here and local commits for completed milestones. Do not publish/push, create worktrees, delete user data or migrate worlds without a separate request.
- Build with ./gradlew.ps1 build. Distinguish implemented, tested on server, and seen in client. Never claim checks that did not run.
- Working documentation lives OUTSIDE this Git repository in ../docs/MIGRATION_STATUS.md and ../docs/HANDOFF.md, as requested by the user. Keep plans, status and handoff notes outside the repository. Never package legacy jars, reference code, private tools, test code, worlds or logs in the release.
