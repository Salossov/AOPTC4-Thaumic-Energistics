package thaumicenergistics.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import thaumicenergistics.extras.ExtrasConfig;

/** Original process-wide extra feature switches, read before recipes and research registration. */
public final class ThEContentConfig {
    public static final ModConfigSpec.BooleanValue WRENCH_FOCUS;
    public static final ModConfigSpec.BooleanValue CERTUS_QUARTZ_DUPLICATION;
    public static final ModConfigSpec.BooleanValue FORCE_TC_FACADES;
    public static final ModConfigSpec SPEC;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("crafting");
        WRENCH_FOCUS = builder.comment("Original Wrench Focus switch: enable the focus behavior, recipe and research.")
                .translation("thaumicenergistics.configuration.wrenchFocus").gameRestart().define("wrenchFocus", true);
        CERTUS_QUARTZ_DUPLICATION = builder.comment("Original Certus Quartz Duplication switch: enable crucible recipes and research.")
                .translation("thaumicenergistics.configuration.certusQuartzDuplication").gameRestart().define("certusQuartzDuplication", true);
        FORCE_TC_FACADES = builder.comment("Original Force TC Facades switch: add the supported Thaumcraft blocks to AE2's facade whitelist.")
                .translation("thaumicenergistics.configuration.forceTcFacades").gameRestart().define("forceTcFacades", true);
        builder.pop();
        SPEC = builder.build();
    }

    public static void initialize(ModContainer container) {
        container.registerConfig(ModConfig.Type.STARTUP, SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ThEClientConfig.SPEC);
        ExtrasConfig.install(new ExtrasConfig.Access() {
            @Override public boolean wrenchFocus() { return WRENCH_FOCUS.get(); }
            @Override public boolean certusDupe() { return CERTUS_QUARTZ_DUPLICATION.get(); }
            @Override public boolean forceTCFacades() { return FORCE_TC_FACADES.get(); }
        });
    }

    private ThEContentConfig() { }
}
