package thaumicenergistics.api;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Modern block-ID equivalent of ThETransportPermissions' explicit legacy class whitelist. */
public final class ContainerPermissions {
    public record Policy(boolean extract, boolean insert, int capacity) {
        public Policy { if (capacity < 0) throw new IllegalArgumentException("Negative container capacity"); }
    }
    private static final Map<ResourceLocation, Policy> POLICIES = new LinkedHashMap<>();
    public static synchronized void register(ResourceLocation block, boolean extract, boolean insert, int capacity) {
        var policy = new Policy(extract, insert, capacity);
        var previous = POLICIES.putIfAbsent(block, policy);
        if (previous != null && !previous.equals(policy)) throw new IllegalArgumentException("Conflicting essentia permission for " + block);
    }
    public static synchronized Policy get(ResourceLocation block) { return POLICIES.get(block); }
    public static void registerDefaults() {
        // EssentiaTileContainerHelper.registerDefaultContainers, using the corresponding port's IDs.
        register(ResourceLocation.parse("thaumcraft:arcane_alembic"), true, false, 32);
        register(ResourceLocation.parse("thaumcraft:alchemical_centrifuge"), true, false, 0);
        register(ResourceLocation.parse("thaumcraft:warded_jar"), true, true, 64);
        register(ResourceLocation.parse("thaumcraft:void_jar"), true, true, 64);
        register(ResourceLocation.parse("thaumcraft:essentia_buffer"), true, false, 8);
        register(ResourceLocation.parse("thaumcraft:essentia_reservoir"), true, true, 256);
        // Advanced-furnace nozzle and ThE's vibration chamber are added with their own units.
    }
    private ContainerPermissions() {}
}
