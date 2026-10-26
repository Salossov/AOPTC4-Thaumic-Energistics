package thaumicenergistics.config;

/** Recipe-condition selectors; availability does not recursively disable a feature's descendants. */
public final class ContentFeature {
    public static boolean enabled(String feature) {
        return switch (feature) {
            case "arcane_assembler" -> ThEContentConfig.ARCANE_ASSEMBLER.get();
            case "arcane_crafting_terminal" -> ThEContentConfig.ARCANE_TERMINAL.get();
            case "distillation_pattern_encoder" -> ThEContentConfig.DISTILLATION_ENCODER.get();
            case "essentia_cells" -> ThEContentConfig.ESSENTIA_CELLS.get();
            case "essentia_provider" -> ThEContentConfig.ESSENTIA_PROVIDER.get();
            case "essentia_vibration_chamber" -> ThEContentConfig.VIBRATION_CHAMBER.get();
            case "golem_wireless_backpack" -> ThEContentConfig.GOLEM_BACKPACK.get();
            case "infusion_provider" -> ThEContentConfig.INFUSION_PROVIDER.get();
            case "io_buses" -> ThEContentConfig.IO_BUSES.get();
            case "vis_interface" -> ThEContentConfig.VIS_INTERFACE.get();
            case "wireless_essentia_terminal" -> ThEContentConfig.WIRELESS_TERMINAL.get();
            default -> false;
        };
    }

    public static boolean researchEnabled(String name) {
        return switch (name) {
            case "tearcaneassembler", "teknowledgeinscriber" -> enabled("arcane_assembler");
            case "tearcaneterm" -> enabled("arcane_crafting_terminal");
            case "tedistillationpatternencoder" -> enabled("distillation_pattern_encoder");
            case "testorage" -> enabled("essentia_cells");
            case "teessprov" -> enabled("essentia_provider");
            case "teessvibcmbr" -> enabled("essentia_vibration_chamber");
            case "tegolembackpack" -> enabled("golem_wireless_backpack");
            case "teinfprov" -> enabled("infusion_provider");
            case "tevisint" -> enabled("vis_interface");
            // Source TEIO and TEESSTERM always exist; their optional pages are handled separately.
            case "teio", "teessterm", "teresearch", "tecores" -> true;
            default -> false;
        };
    }

    /** ThEThaumcraftResearchFeature.getFirstValidParentKey(true), for the configurable Vis branch. */
    public static String firstAvailableParent(String name) {
        String current = name;
        while (!researchEnabled(current)) {
            current = switch (current) {
                case "tearcaneassembler" -> "tevisint";
                case "tevisint" -> "tearcaneterm";
                case "tearcaneterm", "testorage" -> "teresearch";
                default -> throw new IllegalArgumentException("No source fallback for " + current);
            };
        }
        return "thaumicenergistics:" + current;
    }

    private ContentFeature() { }
}
