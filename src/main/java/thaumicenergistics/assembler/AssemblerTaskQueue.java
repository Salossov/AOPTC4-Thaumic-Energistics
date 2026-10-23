package thaumicenergistics.assembler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.knowledge.ArcaneCraftingPattern;

/** One source machine owns one task. Unknown saved tasks block acceptance without erasing data. */
public final class AssemblerTaskQueue {
    private AssemblerCraftingJob job;
    private CompoundTag opaque;
    private boolean accepting, ticking, discarded;

    public boolean isBusy() { return job != null || opaque != null || accepting || ticking; }
    public boolean hasUnreadableTask() { return opaque != null; }
    public int paidTicks() { return job == null ? 0 : job.paidTicks(); }
    public boolean delivering() { return job != null && job.phase() == AssemblerCraftingJob.Phase.DELIVERING; }
    public ItemStack definition() { return job == null ? ItemStack.EMPTY : job.patternDefinition(); }

    public boolean accept(IPatternDetails requested, KeyCounter[] inputs, ServerLevel level,
                           Supplier<List<ArcaneCraftingPattern>> available, BooleanSupplier validHost) {
        requireThread(level);
        if (isBusy() || discarded || !validHost.getAsBoolean()
                || !(requested instanceof ArcaneCraftingPattern arcane) || !arcane.isPatternValid()) return false;
        accepting = true;
        try {
            // Use a fresh pattern from the current core, never the caller's mutable pattern object.
            var definition = arcane.getDefinition();
            ArcaneCraftingPattern current = available.get().stream()
                    .filter(pattern -> pattern.isPatternValid() && pattern.getDefinition().equals(definition))
                    .findFirst().orElse(null);
            if (current == null) return false;
            var plan = AssemblerInputPlan.prepare(current, inputs, level);
            if (plan.isEmpty() || discarded || !validHost.getAsBoolean()
                    || available.get().stream().noneMatch(pattern -> pattern.isPatternValid()
                            && pattern.getDefinition().equals(definition))) return false;
            var committed = plan.get().commit(level);
            if (committed.isEmpty()) return false;
            job = committed.get();
            return true;
        } finally {
            accepting = false;
        }
    }

    public AssemblerCraftingJob.Update tick(ServerLevel level, boolean active, int speed, AssemblerArmor armor,
                                            AssemblerVisBuffer vis, AssemblerCraftingJob.EnergySource energy,
                                            AssemblerCraftingJob.OutputSink output) {
        requireThread(level);
        if (job == null || accepting || ticking || opaque != null)
            return new AssemblerCraftingJob.Update(AssemblerCraftingJob.Status.INACTIVE, false);
        ticking = true;
        try {
            var update = job.tick(active, speed, armor, vis, energy, output);
            if (discarded || job.complete()) {
                job = null;
                return new AssemblerCraftingJob.Update(AssemblerCraftingJob.Status.COMPLETE, true);
            }
            return update;
        } finally {
            ticking = false;
        }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        if (opaque != null) return opaque.copy();
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        if (job != null) tag.put("Job", job.save(registries));
        return tag;
    }

    /** Called by the owning BE's load lifecycle, not from client requests or sync snapshots. */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (accepting || ticking) throw new IllegalStateException("Cannot reload an active assembler callback");
        job = null;
        opaque = null;
        discarded = false;
        if (tag.isEmpty()) return;
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || tag.contains("Job") && !tag.contains("Job", Tag.TAG_COMPOUND)) {
            opaque = tag.copy();
            return;
        }
        if (!tag.contains("Job")) return;
        try {
            var loaded = AssemblerCraftingJob.load(tag.getCompound("Job"), registries);
            if (!loaded.complete()) job = loaded;
        } catch (RuntimeException invalid) {
            // Preserve the entire wrapper, including unknown data; a rejected new job must not overwrite it.
            opaque = tag.copy();
        }
    }

    /** Quirk kept: source machine breaking loses in-flight work. Never call this on chunk unload. */
    public void discardOnBreak() {
        discarded = true;
        if (!ticking) job = null;
        opaque = null;
    }

    private static void requireThread(ServerLevel level) {
        if (level == null || !level.getServer().isSameThread())
            throw new IllegalStateException("Assembler tasks require the server game thread");
    }
}
