# Credits and scope

Unofficial port of Thaumic Energistics 1.1.3.0 by Nividica (Chris), originally licensed under MIT; see LICENSE.

Original Thaumcraft by Azanor, Applied Energistics by AlgorithmX2 and contributors, ExtraCells by M3gaFr3ak. Original ThE textures credit CyanideX. Preserve the original localization and contributor credits from the pinned upstream source.

The original Thaumcraft code/assets are not relicensed by this notice. This standalone addon depends on the user's separate AOP: Thaumcraft 4 runtime and its public API. No distribution of that runtime is performed by this project.

The port targets Minecraft 1.21.1 / NeoForge. Build checks and in-game acceptance are separate; no upstream endorsement is implied.

## Applied Energistics 2 RV3 assembler effect

The adapted client classes AssemblerAnimationEffects and AssemblerCraftingParticle, and the original BlockEnergyParticle texture (stored as textures/particle/assembler_energy.png), derive from Applied Energistics 2 by AlgorithmX2 and contributors, copyright2013-2014, under LGPL-3.0-or-later. Original source: https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/ed54f03e7022e1509a0d722a7014fd7a5e6cdbf8 (rv3.beta.6). Their notices remain in the source; license texts are included under licenses/AE2-LGPL-3.0.txt and licenses/GPL-3.0.txt. This attribution does not relicense the independent Thaumic Energistics MIT code or the separate Thaumcraft runtime/assets. The adaptation uses modern baked item models and a cosmetic particle controller instead of adding a pickable world entity.

The unchanged AE2 RV3 GUI atlas at textures/gui/ae2_rv3_states.png comes from the same commit and license. Its four search-mode cells restore the original Thaumic Energistics GuiButtonSearchMode/AEStateIconsEnum rendering; the old cells in modern AE2's atlas are placeholders.

## Modern AE2 storage cell sprites

The five textures/items/modern_essentia_cell_*.png sprites derive from the item storage cell and storage_cell_led sprites in Applied Energistics 2 NeoForge 19.2.18. Their neutral chassis is recolored green; the original capacity colors, LED colors and pixel grid are preserved. The corresponding drive-atlas sprites preserve the AE2 slot geometry and transparency. Textures and models: copyright 2020 Ridanisaurus Rid and copyright 2013-2020 AlgorithmX2 et al, CC BY-NC-SA 3.0. These derived sprites retain that license; the independent MIT addon code is not relicensed. See licenses/AE2-ASSETS-NOTICE.md and https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/neoforge%2Fv19.2.18/README.md#license.
