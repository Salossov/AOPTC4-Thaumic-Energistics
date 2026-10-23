package thaumicenergistics.assembler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumicenergistics.knowledge.ArcaneCraftingPattern;

/** Prepared ownership transfer from AE2's per-input counters to one persistent job. */
public final class AssemblerInputPlan {
    private final KeyCounter[] counters;
    private final List<Map<AEKey, Long>> snapshots;
    private final AssemblerCraftingJob job;
    private final ServerLevel preparedLevel;
    private final int preparedTick;
    private boolean committed;

    private AssemblerInputPlan(KeyCounter[] counters, List<Map<AEKey, Long>> snapshots,
                               AssemblerCraftingJob job, ServerLevel level) {
        this.counters = counters.clone();
        this.snapshots = List.copyOf(snapshots);
        this.job = job;
        this.preparedLevel = level;
        this.preparedTick = level.getServer().getTickCount();
    }

    /** No counter mutations, even when a later ingredient or the live recipe rejects the plan. */
    public static Optional<AssemblerInputPlan> prepare(ArcaneCraftingPattern pattern, KeyCounter[] offered,
                                                      ServerLevel level) {
        requireThread(level);
        if (pattern == null || !pattern.isPatternValid() || offered == null) return Optional.empty();
        var ingredients = pattern.getIngredients();
        int occupied = 0;
        for (Object ingredient : ingredients) if (ingredient != null) occupied++;
        if (occupied == 0 || offered.length != occupied) return Optional.empty();
        var seen = new IdentityHashMap<KeyCounter, Boolean>();
        var snapshots = new ArrayList<Map<AEKey, Long>>(offered.length);
        var grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        int inputIndex = 0;
        for (int slot = 0; slot < ingredients.length; slot++) {
            Object ingredient = ingredients[slot];
            if (ingredient == null) continue;
            KeyCounter counter = offered[inputIndex++];
            // AE2 owns a distinct counter for each IInput. Never spend an aliased counter twice.
            if (counter == null || seen.put(counter, Boolean.TRUE) != null) return Optional.empty();
            Map<AEKey, Long> snapshot = snapshot(counter);
            if (snapshot == null || snapshot.size() != 1) return Optional.empty();
            var entry = snapshot.entrySet().iterator().next();
            if (!(entry.getKey() instanceof AEItemKey key) || entry.getValue() > Integer.MAX_VALUE)
                return Optional.empty();
            ItemStack stack = key.toStack(entry.getValue().intValue());
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()
                    || !matchesQuantity(ingredient, stack)) return Optional.empty();
            snapshots.add(Map.copyOf(snapshot));
            grid.set(slot, stack);
        }
        // A recipe reload or coupled substitutions must not turn a stale definition into an item source.
        if (!matchesLiveRecipe(pattern, grid, level)) return Optional.empty();
        var job = new AssemblerCraftingJob(pattern.getDefinition().toStack(), pattern.getAspects(),
                sourceResults(pattern));
        return Optional.of(new AssemblerInputPlan(offered, snapshots, job, level));
    }

    private static boolean matchesQuantity(Object ingredient, ItemStack stack) {
        if (ingredient instanceof ItemStack target) {
            return target.getCount() == stack.getCount() && ArcaneCraftingPattern.canSubstituteFor(target, stack);
        }
        if (ingredient instanceof List<?> alternatives) {
            for (Object alternative : alternatives) {
                if (alternative instanceof ItemStack target && matchesQuantity(target, stack)) return true;
            }
        }
        return false;
    }

    /** Source setupAEResults uses encoded prototypes, including the first substitution alternative. */
    private static List<ItemStack> sourceResults(ArcaneCraftingPattern pattern) {
        var results = new ArrayList<ItemStack>();
        for (Object ingredient : pattern.getIngredients()) {
            ItemStack prototype = ItemStack.EMPTY;
            if (ingredient instanceof ItemStack stack) prototype = stack;
            else if (ingredient instanceof List<?> list && !list.isEmpty()
                    && list.getFirst() instanceof ItemStack stack) prototype = stack;
            if (prototype.isEmpty()) continue;
            if (prototype.getItem().hasCraftingRemainingItem(prototype)) {
                ItemStack remainder = prototype.getItem().getCraftingRemainingItem(prototype);
                if (!remainder.isEmpty()) results.add(remainder.copy());
            } else if (prototype.getCount() > 1) {
                results.add(prototype.copyWithCount(prototype.getCount() - 1));
            } else if (BuiltInRegistries.ITEM.getKey(prototype.getItem()).equals(
                    ResourceLocation.parse("thaumcraft:primal_pearl"))) {
                results.add(prototype.copy());
            }
        }
        // Quirk kept: source returns containers/prototype leftovers before the product.
        results.add(pattern.getResult());
        return results;
    }

    @SuppressWarnings("unchecked")
    private static boolean matchesLiveRecipe(ArcaneCraftingPattern pattern, List<ItemStack> grid, ServerLevel level) {
        var registered = BuiltInRegistries.RECIPE_TYPE.getOptional(ResourceLocation.parse("thaumcraft:arcane"));
        if (registered.isEmpty()) return false;
        var type = (RecipeType<IArcaneRecipe>) registered.get();
        for (var holder : level.getRecipeManager().getAllRecipesFor(type)) {
            var input = CraftingInput.ofPositioned(3, 3, grid.stream().map(ItemStack::copy).toList()).input();
            IArcaneRecipe recipe = holder.value();
            // Research is embodied in the encoded knowledge core, as in the original machine;
            // no synthetic player or new per-player automation gate is introduced here.
            if (!recipe.matches(input, level) || !ItemStack.matches(pattern.getResult(),
                    recipe.assemble(input, level.registryAccess()))) continue;
            var costs = new LinkedHashMap<Aspect, Integer>();
            boolean valid = true;
            for (var entry : recipe.vis(input).entrySet()) {
                Aspect aspect = Aspect.getAspect(entry.getKey());
                Integer amount = entry.getValue();
                if (aspect == null || !aspect.isPrimal() || amount == null || amount < 0) { valid = false; break; }
                if (amount > 0) costs.put(aspect, amount);
            }
            if (valid && costs.equals(pattern.getAspects())) return true;
        }
        return false;
    }

    /** Null means malformed; zero entries do not own material and need not be removed. */
    private static Map<AEKey, Long> snapshot(KeyCounter counter) {
        var result = new HashMap<AEKey, Long>();
        for (var entry : counter) {
            if (entry.getKey() == null || entry.getLongValue() < 0) return null;
            if (entry.getLongValue() > 0) result.put(entry.getKey(), entry.getLongValue());
        }
        return result;
    }

    /** All counters are rechecked before the first debit. A plan can transfer ownership only once. */
    public Optional<AssemblerCraftingJob> commit(ServerLevel level) {
        requireThread(level);
        if (committed || level != preparedLevel || level.getServer().getTickCount() != preparedTick)
            return Optional.empty();
        for (int i = 0; i < counters.length; i++) {
            if (!snapshots.get(i).equals(snapshot(counters[i]))) return Optional.empty();
        }
        for (int i = 0; i < counters.length; i++) {
            for (var entry : snapshots.get(i).entrySet()) counters[i].remove(entry.getKey(), entry.getValue());
            counters[i].removeZeros();
        }
        committed = true;
        return Optional.of(job);
    }

    private static void requireThread(ServerLevel level) {
        if (level == null || !level.getServer().isSameThread())
            throw new IllegalStateException("Assembler inputs require the server game thread");
    }
}
