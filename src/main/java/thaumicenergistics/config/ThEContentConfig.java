package thaumicenergistics.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import thaumicenergistics.extras.ExtrasConfig;

/** Original process-wide crafting switches, read before recipes and research registration. */
public final class ThEContentConfig {
    public static final ModConfigSpec.BooleanValue WRENCH_FOCUS;
    public static final ModConfigSpec.BooleanValue CERTUS_QUARTZ_DUPLICATION;
    public static final ModConfigSpec.BooleanValue FORCE_TC_FACADES;
    public static final ModConfigSpec.BooleanValue ARCANE_ASSEMBLER, ARCANE_TERMINAL, DISTILLATION_ENCODER,
            ESSENTIA_CELLS, ESSENTIA_PROVIDER, VIBRATION_CHAMBER, GOLEM_BACKPACK, INFUSION_PROVIDER,
            IO_BUSES, VIS_INTERFACE, WIRELESS_TERMINAL;
    public static final ModConfigSpec SPEC;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("crafting");
        ARCANE_ASSEMBLER = crafting(builder, "arcaneAssembler", "Arcane Assembler and its Knowledge Core/Inscriber", true);
        ARCANE_TERMINAL = crafting(builder, "arcaneCraftingTerminal", "Arcane Crafting Terminal", true);
        DISTILLATION_ENCODER = crafting(builder, "distillationPatternEncoder", "Distillation Pattern Encoder", true);
        ESSENTIA_CELLS = crafting(builder, "essentiaCells", "Essentia Cells, Components, Casing and Workbench", true);
        ESSENTIA_PROVIDER = crafting(builder, "essentiaProvider", "Essentia Provider", true);
        VIBRATION_CHAMBER = crafting(builder, "essentiaVibrationChamber", "Essentia Vibration Chamber", true);
        GOLEM_BACKPACK = crafting(builder, "golemWifiBackpack", "Golem Wifi Backpack", true);
        INFUSION_PROVIDER = crafting(builder, "infusionProvider", "Infusion Provider", true);
        IO_BUSES = crafting(builder, "ioBuses", "Essentia Import/Export Buses (the Storage Bus remains enabled)", true);
        VIS_INTERFACE = crafting(builder, "visRelayInterface", "Vis Relay Interface", true);
        WIRELESS_TERMINAL = crafting(builder, "wirelessEssentiaTerminal", "Wireless Essentia Terminal", true);
        WRENCH_FOCUS = builder.comment("Original Wrench Focus switch: enable the focus behavior, recipe and research.")
                .translation("thaumicenergistics.configuration.wrenchFocus").gameRestart().define("wrenchFocus", true);
        CERTUS_QUARTZ_DUPLICATION = builder.comment("Original Certus Quartz Duplication switch: enable crucible recipes and research.")
                .translation("thaumicenergistics.configuration.certusQuartzDuplication").gameRestart().define("certusQuartzDuplication", true);
        FORCE_TC_FACADES = builder.comment("Original Force TC Facades switch: add the supported Thaumcraft blocks to AE2's facade whitelist.")
                .translation("thaumicenergistics.configuration.forceTcFacades").gameRestart().define("forceTcFacades", true);
        builder.pop();
        SPEC = builder.build();
    }

    private static ModConfigSpec.BooleanValue crafting(ModConfigSpec.Builder builder, String key, String name, boolean enabled) {
        return builder.comment("Original crafting switch: " + name + ". Existing items and stored contents are retained.")
                .translation("thaumicenergistics.configuration." + key).gameRestart().define(key, enabled);
    }

    public static void initialize(ModContainer container) {
        container.registerConfig(ModConfig.Type.STARTUP, SPEC, "thaumicenergistics-startup.toml");
        container.registerConfig(ModConfig.Type.CLIENT, ThEClientConfig.SPEC, "thaumicenergistics-client.toml");
        aop.ui.api.SettingsRegistry.register("thaumicenergistics_aop", "thaumicenergistics", "Thaumic Energistics",
                new aop.ui.api.UiTheme("aop_ui", "thaumicenergistics", 0xffe0edf2, 0xff99b4bd, 0xff6cd4e8), SPEC, ThEClientConfig.SPEC);
        aop.ui.api.SettingsRegistry.registerCategory("thaumicenergistics_aop", "crafting", "thaumicenergistics.configuration.category.crafting");
        aop.ui.api.SettingsRegistry.registerCategory("thaumicenergistics_aop", "client", "thaumicenergistics.configuration.category.client");
        ExtrasConfig.install(new ExtrasConfig.Access() {
            @Override public boolean wrenchFocus() { return WRENCH_FOCUS.get(); }
            @Override public boolean certusDupe() { return CERTUS_QUARTZ_DUPLICATION.get(); }
            @Override public boolean forceTCFacades() { return FORCE_TC_FACADES.get(); }
        });
    }

    private ThEContentConfig() { }
}
