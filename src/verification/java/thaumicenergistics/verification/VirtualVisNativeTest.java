package thaumicenergistics.verification;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.visnet.TileVisNode;
import thaumcraft.api.visnet.VirtualVisSource;
import thaumcraft.api.visnet.VisNetHandler;
import thaumicenergistics.ThaumicEnergistics;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class VirtualVisNativeTest {
    private static final class FiniteSource implements VirtualVisSource {
        int budget;
        byte channel;
        boolean active = true;
        FiniteSource(int budget, byte channel) { this.budget = budget; this.channel = channel; }
        @Override public int range() { return 1; }
        @Override public byte attunement() { return channel; }
        @Override public boolean valid() { return active; }
        @Override public int drain(Aspect aspect, int requested) { int result = Math.min(budget, requested); budget -= result; return result; }
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeAndVirtualSourceLifecycle(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var nativeSourcePos = helper.absolutePos(new BlockPos(1, 2, 1));
        var nativeRelayPos = nativeSourcePos.east(5);
        var relayBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:vis_relay"));
        level.setBlockAndUpdate(nativeSourcePos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:energized_aura_node")).defaultBlockState());
        level.setBlockAndUpdate(nativeRelayPos, relayBlock.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            var nativeRelay = (TileVisNode)level.getBlockEntity(nativeRelayPos);
            VisNetHandler.refresh(level);
            helper.assertTrue(nativeRelay.consumeVis(Aspects.AIR, 10) == 4, "existing real energized source/relay keeps original sqrt(20) budget");
            helper.assertTrue(nativeRelay.consumeVis(Aspects.AIR, 1) == 0, "existing native per-tick finite budget unchanged");
            level.setBlockAndUpdate(nativeSourcePos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(nativeRelayPos, Blocks.AIR.defaultBlockState());

            var ownerPos = helper.absolutePos(new BlockPos(1, 2, 6));
            var relayPos = ownerPos.east(5);
            var consumer = relayPos.east();
            level.setBlockAndUpdate(ownerPos, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(relayPos, relayBlock.defaultBlockState());
            var owner = level.getBlockEntity(ownerPos);
            var source = new FiniteSource(7, (byte)0);
            var registration = VisNetHandler.registerVirtualSource(owner, Direction.EAST, source);
            var relay = (TileVisNode)level.getBlockEntity(relayPos);
            var data = relay.saveWithoutMetadata(level.registryAccess());
            data.putByte("Attunement", (byte)0); relay.loadWithComponents(data, level.registryAccess());
            helper.runAfterDelay(2, () -> {
                VisNetHandler.invalidate(level); VisNetHandler.refresh(level);
                helper.assertTrue(relay.getRootSource() != null && relay.getRootSource().getBlockPos().equals(ownerPos.east()), "native relay resolves virtual source at original one-block offset");
                helper.assertTrue(VisNetHandler.drainVis(level, consumer, Aspects.AIR, 3) == 3 && source.budget == 4, "drain through real relay removes exact finite centivis");
                source.channel = 1; registration.refresh();
                helper.assertTrue(VisNetHandler.drainVis(level, consumer, Aspects.AIR, 3) == 0 && source.budget == 4, "wrong attunement blocks virtual supply");
                source.channel = 0; registration.refresh();
                helper.assertTrue(VisNetHandler.drainVis(level, consumer, Aspects.MAGIC, 1) == 0, "virtual supply rejects compound aspects");
                helper.assertTrue(VisNetHandler.drainVis(level, consumer, Aspects.AIR, 10) == 4 && source.budget == 0, "finite budget cannot be overdrawn");
                source.active = false; registration.refresh(); registration.close(); registration.close();
                helper.assertTrue(VisNetHandler.drainVis(level, consumer, Aspects.AIR, 1) == 0, "invalidated source no longer supplies");
                boolean closedRefresh = false;
                try { registration.refresh(); } catch (IllegalStateException expected) { closedRefresh = true; }
                helper.assertTrue(closedRefresh, "closed registration cannot resume");

                var east = VisNetHandler.registerVirtualSource(owner, Direction.EAST, new FiniteSource(3, (byte)0));
                var west = VisNetHandler.registerVirtualSource(owner, Direction.WEST, new FiniteSource(3, (byte)0));
                boolean duplicate = false;
                try { VisNetHandler.registerVirtualSource(owner, Direction.EAST, new FiniteSource(1, (byte)0)); }
                catch (IllegalStateException expected) { duplicate = true; }
                helper.assertTrue(duplicate, "duplicate face registration rejected");
                helper.assertTrue(VisNetHandler.loadedNodes(level).stream().filter(node -> node.isSource()).count() == 2, "distinct faces do not overwrite each other");
                east.close(); west.close();
                var recursive = VisNetHandler.registerVirtualSource(owner, Direction.EAST, new VirtualVisSource() {
                    @Override public int range() { return 1; }
                    @Override public byte attunement() { return 0; }
                    @Override public boolean valid() { return true; }
                    @Override public int drain(Aspect primal, int amount) { return VisNetHandler.drainVis(level, consumer, primal, amount); }
                });
                helper.assertTrue(VisNetHandler.drainVis(level, consumer, Aspects.AIR, 1) == 0, "recursive virtual route terminates without generating vis");
                boolean wrongThread = CompletableFuture.supplyAsync(() -> {
                    try { recursive.refresh(); return false; } catch (IllegalStateException expected) { return true; }
                }).join();
                helper.assertTrue(wrongThread, "off-thread mutation rejected");
                level.setBlockAndUpdate(ownerPos, Blocks.STONE.defaultBlockState());
                helper.assertTrue(VisNetHandler.drainVis(level, consumer, Aspects.AIR, 1) == 0, "removed/replaced owner immediately unavailable");
                helper.assertTrue(VisNetHandler.loadedNodes(level).stream().noneMatch(TileVisNode::isSource), "stale owner registration pruned");
                recursive.close();
                level.setBlockAndUpdate(ownerPos, Blocks.CHEST.defaultBlockState());
                var passive = VisNetHandler.registerVirtualSource(level.getBlockEntity(ownerPos), Direction.EAST, new FiniteSource(2, (byte)0));
                level.setBlockAndUpdate(ownerPos, Blocks.STONE.defaultBlockState());
                // No VisNet query between replacement and this assertion: cleanup must run on level tick.
                helper.runAfterDelay(2, () -> {
                    boolean autoClosed = false;
                    try { passive.refresh(); } catch (IllegalStateException expected) { autoClosed = true; }
                    helper.assertTrue(autoClosed, "owner replacement auto-closes on server tick without a consumer query");
                    passive.close();
                    level.setBlockAndUpdate(relayPos, Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(ownerPos, Blocks.AIR.defaultBlockState());
                    System.out.println("THE_NATIVE_VIRTUAL_VIS_API_PASS");
                    helper.succeed();
                });
            });
        });
    }
}
