package thaumicenergistics.assembler;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;

/**
 * One accepted assembler task, owned exclusively by its machine's server thread.
 * The provider must prepare canonical results/remainders and consume inputs only
 * when accepting the job. Displays and client packets never create jobs.
 */
public final class AssemblerCraftingJob {
    public enum Phase { CRAFTING, DELIVERING, COMPLETE }
    public enum Status { INACTIVE, WAITING_FOR_VIS, WAITING_FOR_AE, PROGRESSED, OUTPUT_BLOCKED, COMPLETE }
    public record Update(Status status, boolean changed) {}

    @FunctionalInterface
    public interface EnergySource {
        /** Actual extracted AE; SIMULATE must have no side effects. */
        double extract(double requested, Actionable mode);
    }

    @FunctionalInterface
    public interface OutputSink {
        /** MODULATE insertion: return the actual accepted amount, never a remainder. */
        long insert(AEItemKey key, long requested);
    }

    private final ItemStack patternDefinition;
    private final Map<Aspect, Integer> wholeVis;
    private final List<ItemStack> pendingResults;
    private Phase phase;
    private int paidTicks;
    private boolean energyFinished;
    private double partialTickEnergy;

    public AssemblerCraftingJob(ItemStack patternDefinition, Map<Aspect, Integer> wholeVis,
                                List<ItemStack> results) {
        this(patternDefinition, wholeVis, results, Phase.CRAFTING, 0);
    }

    private AssemblerCraftingJob(ItemStack definition, Map<Aspect, Integer> costs,
                                 List<ItemStack> results, Phase phase, double energyCredit) {
        Objects.requireNonNull(definition);
        Objects.requireNonNull(costs);
        Objects.requireNonNull(results);
        if (definition.isEmpty() || definition.getCount() != 1)
            throw new IllegalArgumentException("An individual encoded pattern is required");
        if (costs.isEmpty()) throw new IllegalArgumentException("Arcane Vis cost required");
        var copiedCosts = new LinkedHashMap<Aspect, Integer>();
        costs.forEach((aspect, amount) -> {
            if (aspect == null || !aspect.isPrimal() || amount == null || amount <= 0)
                throw new IllegalArgumentException("Invalid arcane Vis cost");
            copiedCosts.put(aspect, amount);
        });
        if (results.size() > 10 || (results.isEmpty() && phase != Phase.COMPLETE))
            throw new IllegalArgumentException("Expected product and at most nine recipe remainders");
        if (!Double.isFinite(energyCredit) || energyCredit < 0)
            throw new IllegalArgumentException("Invalid partial tick payment");
        this.patternDefinition = definition.copy();
        this.wholeVis = Collections.unmodifiableMap(copiedCosts);
        this.pendingResults = new ArrayList<>(results.size());
        for (ItemStack stack : results) {
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Empty job result");
            this.pendingResults.add(stack.copy());
        }
        if (phase == Phase.COMPLETE && !results.isEmpty())
            throw new IllegalArgumentException("Completed job still owns results");
        this.phase = Objects.requireNonNull(phase);
        this.partialTickEnergy = energyCredit;
        // Original unpaid crafting progress resets on world reload. Paid output must not.
        this.paidTicks = phase == Phase.CRAFTING ? 0 : AssemblerRules.BASE_TICKS;
    }

    public Phase phase() { return phase; }
    public boolean complete() { return phase == Phase.COMPLETE; }
    public int paidTicks() { return paidTicks; }
    public ItemStack patternDefinition() { return patternDefinition.copy(); }
    public Map<Aspect, Integer> wholeVis() { return wholeVis; }
    public List<ItemStack> pendingResults() {
        return pendingResults.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    public Update tick(boolean active, int speedCards, AssemblerArmor armor, AssemblerVisBuffer vis,
                        EnergySource energy, OutputSink output) {
        if (complete()) return new Update(Status.COMPLETE, false);
        if (!active) return new Update(Status.INACTIVE, false);
        // Once Vis was paid, a full/empty Vis buffer or changed armor cannot block delivery.
        if (phase == Phase.DELIVERING) return deliver(Objects.requireNonNull(output), false);

        var costs = Objects.requireNonNull(armor).costs(wholeVis);
        Objects.requireNonNull(vis);
        if (!vis.canPay(costs)) return new Update(Status.WAITING_FOR_VIS, false);
        int duration = AssemblerRules.ticksPerCraft(speedCards);
        boolean changed = false;
        if (!energyFinished) {
            double required = AssemblerRules.powerPerTick(speedCards, armor.warpMultiplier());
            if (!Double.isFinite(required) || required < 0)
                throw new IllegalArgumentException("Invalid assembler power requirement");
            double missing = Math.max(0, required - partialTickEnergy);
            if (missing > 0) {
                Objects.requireNonNull(energy);
                if (checkedExtraction(energy.extract(missing, Actionable.SIMULATE), missing) < missing)
                    return new Update(Status.WAITING_FOR_AE, false);
                double paid = checkedExtraction(energy.extract(missing, Actionable.MODULATE), missing);
                if (paid < missing) {
                    // A changed network may pay less than simulation promised. Keep that payment,
                    // but never advance a crafting tick until its full cost has been covered.
                    partialTickEnergy += paid;
                    return new Update(Status.WAITING_FOR_AE, paid > 0);
                }
                partialTickEnergy += paid;
            }
            partialTickEnergy = Math.max(0, partialTickEnergy - required);
            paidTicks++;
            changed = true;
            energyFinished = paidTicks >= duration;
            if (!energyFinished) return new Update(Status.PROGRESSED, true);
        }

        // If an energy callback changed the Vis buffer, retain completed paid work for retry.
        if (!vis.pay(costs)) return new Update(Status.WAITING_FOR_VIS, changed);
        phase = Phase.DELIVERING;
        return deliver(Objects.requireNonNull(output), true);
    }

    private static double checkedExtraction(double extracted, double requested) {
        double tolerance = 4 * Math.ulp(requested);
        if (!Double.isFinite(extracted) || extracted < 0 || extracted > requested + tolerance)
            throw new IllegalStateException("Energy source violated extraction contract");
        return extracted >= requested - tolerance ? requested : extracted;
    }

    private Update deliver(OutputSink output, boolean changed) {
        boolean remaining = false;
        for (ItemStack stack : pendingResults) {
            if (stack.isEmpty()) continue;
            long requested = stack.getCount();
            var key = Objects.requireNonNull(AEItemKey.of(stack));
            long accepted = output.insert(key, requested);
            if (accepted < 0 || accepted > requested)
                throw new IllegalStateException("Output sink violated insertion contract");
            if (accepted > 0) {
                stack.shrink((int) accepted);
                changed = true;
            }
            remaining |= !stack.isEmpty();
        }
        if (remaining) return new Update(Status.OUTPUT_BLOCKED, changed);
        phase = Phase.COMPLETE;
        return new Update(Status.COMPLETE, true);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putString("Phase", phase.name());
        tag.put("Pattern", patternDefinition.save(registries));
        var costs = new CompoundTag();
        wholeVis.forEach((aspect, amount) -> costs.putInt(aspect.getTag(), amount));
        tag.put("WholeVis", costs);
        var results = new ListTag();
        for (ItemStack stack : pendingResults) if (!stack.isEmpty()) results.add(stack.save(registries));
        tag.put("Results", results);
        tag.putDouble("PartialTickEnergy", partialTickEnergy);
        return tag;
    }

    /** Unknown/damaged formats throw; the owning BE must preserve their raw tag, not erase it. */
    public static AssemblerCraftingJob load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 ||
                !tag.contains("Phase", Tag.TAG_STRING) || !tag.contains("Pattern", Tag.TAG_COMPOUND) ||
                !tag.contains("WholeVis", Tag.TAG_COMPOUND) || !tag.contains("Results", Tag.TAG_LIST) ||
                !tag.contains("PartialTickEnergy", Tag.TAG_DOUBLE))
            throw new IllegalArgumentException("Unknown or damaged assembler job");
        Phase phase = Phase.valueOf(tag.getString("Phase"));
        ItemStack definition = ItemStack.parse(registries, tag.getCompound("Pattern"))
                .orElseThrow(() -> new IllegalArgumentException("Invalid assembler pattern"));
        var costs = new LinkedHashMap<Aspect, Integer>();
        var visTag = tag.getCompound("WholeVis");
        for (String name : visTag.getAllKeys()) {
            Aspect aspect = Aspect.getAspect(name);
            if (!visTag.contains(name, Tag.TAG_INT) || aspect == null || !aspect.isPrimal())
                throw new IllegalArgumentException("Invalid job Vis aspect");
            costs.put(aspect, visTag.getInt(name));
        }
        var rawResults = (ListTag) tag.get("Results");
        if (rawResults.size() > 10 || (!rawResults.isEmpty() && rawResults.getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid job result list");
        var results = new ArrayList<ItemStack>(rawResults.size());
        for (Tag entry : rawResults) results.add(ItemStack.parse(registries, (CompoundTag) entry)
                .orElseThrow(() -> new IllegalArgumentException("Invalid job result")));
        return new AssemblerCraftingJob(definition, costs, results, phase, tag.getDouble("PartialTickEnergy"));
    }
}
