package thaumicenergistics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local presentation settings; no effect on stored Vis, recipes, networks or inventories. */
public final class ThEClientConfig {
    public static final ModConfigSpec.BooleanValue DISABLE_GEARBOX_MODEL;
    public static final ModConfigSpec.BooleanValue SEARCH_TOOLTIPS;
    public static final ModConfigSpec SPEC;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("client");
        DISABLE_GEARBOX_MODEL = builder.comment("Original Disable Gearbox Model switch: render iron and thaumium gearboxes as ordinary cubes.")
                .translation("thaumicenergistics.configuration.disableGearboxModel").define("disableGearboxModel", false);
        SEARCH_TOOLTIPS = builder.comment("Restore AE2 RV3's SEARCH_TOOLTIPS option for the arcane terminal; AE2 19 no longer exposes it.",
                        "True searches item tooltips as well as names/mod IDs, including @ queries, as in the original default.")
                .translation("thaumicenergistics.configuration.searchTooltips").define("searchTooltips", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ThEClientConfig() { }
}
