package thaumicenergistics.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumcraft.api.aspects.IAspectContainer;
import thaumicenergistics.api.ContainerPermissions;

/** Port of EssentiaTileContainerHelper, using only the core's public container capability. */
public final class ContainerStorage implements MEStorage {
    private final IAspectContainer container;
    private final ContainerPermissions.Policy policy;
    private final boolean allowVoid;
    private final Component description;

    public ContainerStorage(IAspectContainer container, ContainerPermissions.Policy policy, boolean allowVoid, Component description) {
        this.container = java.util.Objects.requireNonNull(container);
        this.policy = java.util.Objects.requireNonNull(policy);
        this.allowVoid = allowVoid;
        this.description = description;
    }
    public static ContainerStorage at(Level level, BlockPos pos, boolean allowVoid) {
        if (!level.hasChunkAt(pos)) return null;
        var block = level.getBlockState(pos).getBlock();
        var policy = ContainerPermissions.get(BuiltInRegistries.BLOCK.getKey(block));
        if (policy == null) return null;
        var container = level.getCapability(EssentiaCapabilities.CONTAINER, pos, null);
        return container == null ? null : new ContainerStorage(container, policy, allowVoid, block.getName());
    }
    public EssentiaKey dominantAspect() {
        var contents = container.getAspects();
        if (contents == null) return null;
        Aspect aspect = null;
        int maximum = 0;
        for (var entry : contents.asMap().entrySet()) if (entry.getValue() > maximum) {
            aspect = entry.getKey(); maximum = entry.getValue();
        }
        return aspect == null ? null : EssentiaKey.of(aspect);
    }
    @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (amount == 0 || !policy.insert() || !(what instanceof EssentiaKey key)) return 0;
        var aspect = key.aspect();
        if (aspect == null) return 0;
        var snapshot = container.getAspects();
        long stored = snapshot == null ? 0 : snapshot.asMap().values().stream().mapToLong(Integer::longValue).sum();
        boolean jar = container.getJarType() != IAspectContainer.JarType.NONE;
        var current = dominantAspect();
        // Original jar matching must work even when doesContainerAccept reports a full jar.
        if (jar && current != null && !current.equals(key)) return 0;
        if ((!jar || current == null) && !container.doesContainerAccept(aspect)) return 0;
        var filter = container.getAspectFilter();
        if (jar && filter != null && filter != aspect) return 0;
        long room = allowVoid && container.getJarType() == IAspectContainer.JarType.VOID
                ? Integer.MAX_VALUE : Math.max(0, policy.capacity() - stored);
        int accepted = (int)Math.min(Math.min(amount, room), Integer.MAX_VALUE);
        if (accepted <= 0 || mode == Actionable.SIMULATE) return accepted;
        // Public TC contract returns the remainder, unlike MEStorage and transport capabilities.
        int remainder = container.addToContainer(aspect, accepted);
        if (remainder < 0 || remainder > accepted) throw new IllegalStateException("Invalid container insertion result");
        return accepted - remainder;
    }
    @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (amount == 0 || !policy.extract() || !(what instanceof EssentiaKey key) || key.aspect() == null) return 0;
        int available = Math.max(0, container.containerContains(key.aspect()));
        int extracted = (int)Math.min(amount, available);
        if (extracted == 0 || mode == Actionable.SIMULATE) return extracted;
        return container.takeFromContainer(key.aspect(), extracted) ? extracted : 0;
    }
    @Override public void getAvailableStacks(KeyCounter counter) {
        if (!policy.extract()) return;
        var contents = container.getAspects();
        if (contents != null) contents.asMap().forEach((aspect, amount) -> {
            if (amount > 0) counter.add(EssentiaKey.of(aspect), amount);
        });
    }
    @Override public Component getDescription() { return description; }
}
