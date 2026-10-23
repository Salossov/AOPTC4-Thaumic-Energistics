package thaumicenergistics.assembler;

import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import thaumicenergistics.knowledge.ArcaneCraftingPattern;

/** Native AE2 service for the assembler's server-owned queue; the machine supplies lifecycle and Vis. */
public final class AssemblerCraftingProvider implements ICraftingProvider {
    private final IManagedGridNode node;
    private final Supplier<ServerLevel> level;
    private final BooleanSupplier validHost;
    private final Supplier<List<ArcaneCraftingPattern>> patterns;
    private final AssemblerTaskQueue queue;
    private final IActionSource source;
    private final Runnable saveChanges;

    public AssemblerCraftingProvider(IManagedGridNode node, Supplier<ServerLevel> level, BooleanSupplier validHost,
                                     Supplier<List<ArcaneCraftingPattern>> patterns, AssemblerTaskQueue queue,
                                     IActionSource source, Runnable saveChanges) {
        this.node = Objects.requireNonNull(node);
        this.level = Objects.requireNonNull(level);
        this.validHost = Objects.requireNonNull(validHost);
        this.patterns = Objects.requireNonNull(patterns);
        this.queue = Objects.requireNonNull(queue);
        this.source = Objects.requireNonNull(source);
        this.saveChanges = Objects.requireNonNull(saveChanges);
    }

    @Override public List<IPatternDetails> getAvailablePatterns() {
        if (level.get() == null || !validHost.getAsBoolean()) return List.of();
        return patterns.get().stream().filter(ArcaneCraftingPattern::isPatternValid)
                .map(pattern -> (IPatternDetails) pattern.copy()).toList();
    }

    @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        ServerLevel current = level.get();
        if (current == null || !current.getServer().isSameThread() || !node.isActive()) return false;
        if (!queue.accept(pattern, inputs, current, patterns, () -> validHost.getAsBoolean() && node.isActive()))
            return false;
        saveChanges.run();
        return true;
    }

    @Override public boolean isBusy() { return queue.isBusy(); }

    public void patternsChanged() { ICraftingProvider.requestUpdate(node); }

    public AssemblerCraftingJob.Update tick(int speed, AssemblerArmor armor, AssemblerVisBuffer vis) {
        ServerLevel current = level.get();
        if (current == null || !validHost.getAsBoolean())
            return new AssemblerCraftingJob.Update(AssemblerCraftingJob.Status.INACTIVE, false);
        var grid = node.getGrid();
        var update = queue.tick(current, node.isActive() && grid != null, speed, armor, vis,
                (requested, mode) -> validHost.getAsBoolean() && node.isActive() && node.getGrid() == grid
                        ? grid.getEnergyService().extractAEPower(requested, mode, PowerMultiplier.CONFIG) : 0,
                (key, requested) -> validHost.getAsBoolean() && node.isActive() && node.getGrid() == grid
                        ? grid.getStorageService().getInventory().insert(key, requested,
                                appeng.api.config.Actionable.MODULATE, source) : 0);
        if (update.changed()) saveChanges.run();
        return update;
    }
}
