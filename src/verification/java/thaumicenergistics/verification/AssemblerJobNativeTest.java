package thaumicenergistics.verification;

import appeng.api.config.Actionable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.assembler.AssemblerArmor;
import thaumicenergistics.assembler.AssemblerCraftingJob;
import thaumicenergistics.assembler.AssemblerVisBuffer;

/** Deferred job-state tests. These do not claim CPU/provider or machine integration coverage. */
@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class AssemblerJobNativeTest {
    private static AssemblerCraftingJob job() {
        // The state machine owns an opaque definition. Real pattern decoding belongs to the provider.
        return new AssemblerCraftingJob(new ItemStack(Items.PAPER), Map.of(Aspects.AIR, 3),
                List.of(new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.GLASS_BOTTLE)));
    }

    private static AssemblerArmor armor() { return new AssemblerArmor(Map.of(Aspects.AIR, 1F), 1F); }
    private static AssemblerVisBuffer fullVis() { var vis = new AssemblerVisBuffer(); vis.fill(); return vis; }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void partialAeNeverAdvancesAndKeepsPaymentOnReload(GameTestHelper h) {
        var job = job();
        var vis = fullVis();
        var inactive = job.tick(false, 0, null, null,
                (amount, mode) -> { throw new AssertionError("Inactive machine extracted AE"); },
                (key, amount) -> { throw new AssertionError("Inactive machine emitted output"); });
        h.assertTrue(inactive.status() == AssemblerCraftingJob.Status.INACTIVE && job.paidTicks() == 0,
                "Inactive task has no side effects");
        var zero = job.tick(true, 0, armor(), vis, (amount, mode) -> 0, (key, amount) -> amount);
        h.assertTrue(zero.status() == AssemblerCraftingJob.Status.WAITING_FOR_AE && job.paidTicks() == 0,
                "No simulated energy cannot advance progress");
        var partial = job.tick(true, 0, armor(), vis,
                (amount, mode) -> mode == Actionable.SIMULATE ? amount : .5,
                (key, amount) -> { throw new AssertionError("Unpaid task emitted output"); });
        h.assertTrue(partial.changed() && job.paidTicks() == 0 && vis.get(Aspects.AIR) == 1500,
                "Partial actual AE payment neither advances nor consumes Vis");
        var restored = AssemblerCraftingJob.load(job.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        var requests = new ArrayList<Double>();
        restored.tick(true, 0, armor(), vis, (amount, mode) -> {
            if (mode == Actionable.MODULATE) requests.add(amount);
            return amount;
        }, (key, amount) -> { throw new AssertionError("One tick completed the whole recipe"); });
        h.assertTrue(requests.equals(List.of(1D)) && restored.paidTicks() == 1,
                "Reload keeps the .5 AE payment and requests only the missing 1 AE");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void paidOutputSurvivesPartialInsertionAndReload(GameTestHelper h) {
        var job = job();
        var vis = fullVis();
        long[] received = {0, 0};
        for (int i = 0; i < 4; i++) job.tick(true, 4, armor(), vis, (amount, mode) -> amount, (key, amount) -> {
            if (key.toStack().is(Items.DIAMOND)) { received[0] += 2; return 2; }
            return 0;
        });
        h.assertTrue(job.phase() == AssemblerCraftingJob.Phase.DELIVERING && vis.get(Aspects.AIR) == 1470,
                "Four paid speed-card ticks debit exactly 30 raw Vis once");
        h.assertTrue(received[0] == 2 && job.pendingResults().getFirst().getCount() == 2,
                "Partial actual insertion leaves the two rejected diamonds in the job");
        var restored = AssemblerCraftingJob.load(job.save(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        var emptyVis = new AssemblerVisBuffer();
        var blocked = restored.tick(true, 4, null, emptyVis, null, (key, amount) -> 0);
        h.assertTrue(blocked.status() == AssemblerCraftingJob.Status.OUTPUT_BLOCKED && emptyVis.get(Aspects.AIR) == 0,
                "Ready output waits without requesting armor, AE or another Vis reserve");
        restored.tick(true, 4, null, emptyVis, null, (key, amount) -> {
            if (key.toStack().is(Items.DIAMOND)) received[0] += amount;
            else if (key.toStack().is(Items.GLASS_BOTTLE)) received[1] += amount;
            return amount;
        });
        h.assertTrue(restored.complete() && received[0] == 4 && received[1] == 1,
                "Reload emits only unaccepted results, including the recipe container");
        restored.tick(true, 4, null, null, null,
                (key, amount) -> { throw new AssertionError("Completed job duplicated output"); });
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void jobSnapshotsAreIndependentAndUnknownFormatFailsClosed(GameTestHelper h) {
        var definition = new ItemStack(Items.PAPER);
        var product = new ItemStack(Items.DIAMOND, 4);
        var job = new AssemblerCraftingJob(definition, Map.of(Aspects.AIR, 3), List.of(product));
        definition.setCount(16); product.setCount(32);
        job.patternDefinition().setCount(8); job.pendingResults().getFirst().setCount(64);
        h.assertTrue(job.patternDefinition().getCount() == 1 && job.pendingResults().getFirst().getCount() == 4,
                "Caller stacks and returned snapshots cannot alter the task");
        var original = job.save(h.getLevel().registryAccess());
        var changed = original.copy(); changed.putInt("Version", 99);
        boolean refused = false;
        try { AssemblerCraftingJob.load(changed, h.getLevel().registryAccess()); }
        catch (IllegalArgumentException expected) { refused = true; }
        h.assertTrue(refused && changed.getInt("Version") == 99 && original.getInt("Version") == 1,
                "Unknown format is rejected without mutating its raw data");
        h.succeed();
    }
}
