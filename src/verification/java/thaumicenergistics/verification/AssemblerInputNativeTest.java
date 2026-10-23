package thaumicenergistics.verification;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.RecipeDisplay;
import thaumcraft.api.crafting.RecipeDisplays;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.assembler.AssemblerInputPlan;
import thaumicenergistics.assembler.AssemblerTaskQueue;
import thaumicenergistics.knowledge.ArcaneCraftingPattern;

/** Deferred native recipe/counter ownership scenarios; not proof of CPU, machine or client integration. */
@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class AssemblerInputNativeTest {
    private record Fixture(ArcaneCraftingPattern pattern, KeyCounter[] counters, List<AEItemKey> keys) {}

    private static Fixture fixture(GameTestHelper h) {
        var level = h.getLevel();
        for (var display : RecipeDisplays.recipes(level)) {
            if (display.kind() != RecipeDisplay.Kind.ARCANE || display.recipeId() == null
                    || display.inputs().size() != 9) continue;
            var holder = level.getRecipeManager().byKey(display.recipeId()).orElse(null);
            if (holder == null || !(holder.value() instanceof IArcaneRecipe recipe)) continue;
            var grid = new ArrayList<ItemStack>();
            Object[] ingredients = new Object[9];
            var counters = new ArrayList<KeyCounter>();
            var keys = new ArrayList<AEItemKey>();
            boolean usable = true;
            for (int i = 0; i < 9; i++) {
                var ingredient = display.inputs().get(i);
                ItemStack stack = ItemStack.EMPTY;
                if (!ingredient.isEmpty()) {
                    ItemStack[] choices = ingredient.getItems();
                    if (choices.length == 0) { usable = false; break; }
                    stack = choices[0].copyWithCount(1);
                    ingredients[i] = stack.copy();
                    var key = AEItemKey.of(stack);
                    var counter = new KeyCounter();
                    counter.add(key, 1);
                    counters.add(counter);
                    keys.add(key);
                }
                grid.add(stack);
            }
            if (!usable || counters.size() < 2) continue;
            var input = CraftingInput.ofPositioned(3, 3, grid).input();
            if (!recipe.matches(input, level)) continue;
            var costs = new LinkedHashMap<Aspect, Integer>();
            for (var entry : recipe.vis(input).entrySet()) {
                Aspect aspect = Aspect.getAspect(entry.getKey());
                if (aspect == null || !aspect.isPrimal() || entry.getValue() < 0) { usable = false; break; }
                if (entry.getValue() > 0) costs.put(aspect, entry.getValue());
            }
            ItemStack product = recipe.assemble(input, level.registryAccess());
            if (!usable || costs.isEmpty() || product.isEmpty()) continue;
            var pattern = new ArcaneCraftingPattern(ItemStack.EMPTY, costs, product, ingredients, level.registryAccess());
            return new Fixture(pattern, counters.toArray(KeyCounter[]::new), List.copyOf(keys));
        }
        throw new AssertionError("Expected an effective nine-slot arcane recipe with positive primal Vis");
    }

    private static void assertUntouched(GameTestHelper h, Fixture fixture) {
        for (int i = 0; i < fixture.counters.length; i++) {
            h.assertTrue(fixture.counters[i].get(fixture.keys.get(i)) == 1, "Rejected offer retains every real input");
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void changedLateCounterCannotPartiallySpendEarlierInputs(GameTestHelper h) {
        var f = fixture(h);
        var plan = AssemblerInputPlan.prepare(f.pattern, f.counters, h.getLevel()).orElseThrow();
        assertUntouched(h, f);
        int last = f.counters.length - 1;
        f.counters[last].remove(f.keys.get(last), 1);
        h.assertTrue(plan.commit(h.getLevel()).isEmpty(), "A changed final counter invalidates the complete plan");
        for (int i = 0; i < last; i++) {
            h.assertTrue(f.counters[i].get(f.keys.get(i)) == 1, "Validation happens before the first debit");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void removedCorePatternAndBusyQueueKeepOffers(GameTestHelper h) {
        var f = fixture(h);
        var queue = new AssemblerTaskQueue();
        h.assertTrue(!queue.accept(f.pattern, f.counters, h.getLevel(), List::of, () -> true),
                "Caller definitions absent from the live core are refused");
        assertUntouched(h, f);
        h.assertTrue(queue.accept(f.pattern, f.counters, h.getLevel(), () -> List.of(f.pattern), () -> true),
                "A current core pattern accepts one complete offer");
        for (var counter : f.counters) h.assertTrue(counter.isEmpty(), "Accepted counters transfer all material");
        var second = fixture(h);
        h.assertTrue(!queue.accept(second.pattern, second.counters, h.getLevel(), () -> List.of(second.pattern), () -> true),
                "One machine cannot accept a second task while owning the first");
        assertUntouched(h, second);
        var restored = new AssemblerTaskQueue();
        restored.load(queue.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(restored.isBusy() && ItemStack.matches(restored.definition(), queue.definition()),
                "Reload retains ownership of the accepted recipe independently of live input counters");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unknownSavedTaskBlocksWithoutErasure(GameTestHelper h) {
        var unknown = new CompoundTag();
        unknown.putInt("Version", 700);
        unknown.putString("FutureState", "owned ingredients and paid output");
        var queue = new AssemblerTaskQueue();
        queue.load(unknown, h.getLevel().registryAccess());
        var f = fixture(h);
        h.assertTrue(queue.isBusy() && queue.hasUnreadableTask(), "Unknown tasks cannot be replaced by a new job");
        h.assertTrue(!queue.accept(f.pattern, f.counters, h.getLevel(), () -> List.of(f.pattern), () -> true),
                "Blocked queue does not claim the offered materials");
        assertUntouched(h, f);
        h.assertTrue(queue.save(h.getLevel().registryAccess()).equals(unknown), "Unknown task data round-trips intact");
        h.succeed();
    }

    private AssemblerInputNativeTest() {}
}
