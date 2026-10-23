package thaumicenergistics.extras;

import java.util.Objects;

/**
 * Configuration seam for the three original batch-2 feature toggles. This is a
 * single supplier seam, not a second config system: the central ThE config port
 * installs one implementation and every extra reads through it. Until installed,
 * the exact source defaults apply (original ConfigurationHandler DFT_* constants).
 */
public final class ExtrasConfig {
    /** Feature selector values used by {@link ExtrasFeatureCondition}. */
    public static final String WRENCH_FOCUS = "wrench_focus";
    public static final String CERTUS_DUPE = "certus_dupe";
    public static final String TC_FACADES = "tc_facades";

    /** Original defaults: DFT_ENABLE_WRENCH_FOCUS / DFT_ENABLE_QUARTZ_DUPE / DFT_FORCE_TC_FACADES = true. */
    public interface Access {
        boolean wrenchFocus();

        boolean certusDupe();

        boolean forceTCFacades();
    }

    private static volatile Access access = new Access() {
        @Override public boolean wrenchFocus() { return true; }

        @Override public boolean certusDupe() { return true; }

        @Override public boolean forceTCFacades() { return true; }
    };

    /** Installs the central config-backed access; a second install replaces the seam once. */
    public static void install(Access implementation) {
        access = Objects.requireNonNull(implementation);
    }

    public static boolean wrenchFocus() { return access.wrenchFocus(); }

    public static boolean certusDupe() { return access.certusDupe(); }

    public static boolean forceTCFacades() { return access.forceTCFacades(); }

    /** Resolves a {@link ExtrasFeatureCondition} selector value; unknown features stay disabled. */
    public static boolean enabled(String feature) {
        return switch (feature) {
            case WRENCH_FOCUS -> wrenchFocus();
            case CERTUS_DUPE -> certusDupe();
            case TC_FACADES -> forceTCFacades();
            default -> false;
        };
    }

    private ExtrasConfig() {}
}
