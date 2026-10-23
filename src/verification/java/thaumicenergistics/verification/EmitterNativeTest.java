package thaumicenergistics.verification;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.network.EmitterThresholdPayload;
import thaumicenergistics.parts.EssentiaLevelEmitterPart;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaKey;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class EmitterNativeTest {
    @GameTest(template="empty",timeoutTicks=200)
    public static void nativeEssentiaWatcherAndEmitter(GameTestHelper helper) {
        VerificationReports.install();
        var level=helper.getLevel();var cable=helper.absolutePos(new BlockPos(3,2,3));var drive=cable.east();
        level.setBlockAndUpdate(cable.west(),BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(drive,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState());
        var slots=level.getCapability(Capabilities.ItemHandler.BLOCK,drive,Direction.UP);
        helper.assertTrue(slots!=null&&slots.insertItem(0,new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get()),false).isEmpty(),"real drive accepts emitter fixture storage cell");
        PartHelper.setPart(level,cable,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var emitter=PartHelper.setPart(level,cable,Direction.SOUTH,null,ThaumicEnergistics.LEVEL_EMITTER.get());
        var water=EssentiaKey.of(Aspects.WATER);var fire=EssentiaKey.of(Aspects.FIRE);var source=IActionSource.empty();
        helper.runAfterDelay(50,()->{
            helper.assertTrue(emitter.getMainNode().isActive(),"emitter powered and channelled in actual cable host");
            var bounds=level.getBlockState(cable).getShape(level,cable).bounds();
            helper.assertTrue(Math.abs(bounds.maxZ-15/16d)<1e-6&&Math.abs(bounds.minZ-6/16d)<1e-6,"release collision box 7,7,11..9,9,15 extends toward the attached south face");
            emitter.filter(water);emitter.threshold(10);
            helper.assertTrue(!emitter.emitting()&&emitter.currentAmount()==0,"empty selected storage below high threshold");
            var inventory=emitter.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(inventory.insert(water,10,Actionable.MODULATE,source)==10,"real grid accepts water for watcher threshold");
        });
        helper.runAfterDelay(60,()->{
            helper.assertTrue(emitter.currentAmount()==10&&emitter.emitting()&&emitter.isProvidingStrongPower()==15&&emitter.getLightLevel()==7,"AE2 watcher drives exact >= comparison, full redstone and original light");
            helper.assertTrue(level.getSignal(cable,Direction.NORTH)==15,"actual cable block exposes emitter redstone signal toward southern neighbor");
            emitter.lowSignal(true);helper.assertTrue(!emitter.emitting(),"low mode is strict less than threshold");
            var inventory=emitter.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(inventory.extract(water,1,Actionable.MODULATE,source)==1,"actual extraction changes watched total");
        });
        helper.runAfterDelay(70,()->{
            helper.assertTrue(emitter.currentAmount()==9&&emitter.emitting(),"low mode activates after actual watched extraction");
            var inventory=emitter.getMainNode().getGrid().getStorageService().getInventory();
            inventory.insert(fire,5,Actionable.MODULATE,source);
            helper.assertTrue(emitter.currentAmount()==9,"untracked aspect does not alter watched total");
            emitter.filter(fire);
        });
        helper.runAfterDelay(75,()->{
            helper.assertTrue(emitter.currentAmount()==5,"changed watcher reports the actual grid amount on the next storage tick");
            var low=new CompoundTag();emitter.writeToNBT(low,level.registryAccess());
            helper.assertTrue(emitter.lowSignal()&&low.getInt("mode")==1,"low-signal mode keeps the legacy AE2 RedstoneMode ordinal");
            emitter.lowSignal(false);var high=new CompoundTag();emitter.writeToNBT(high,level.registryAccess());
            helper.assertTrue(!high.contains("mode"),"default high-signal mode is omitted like the release");
            emitter.lowSignal(true);
            emitter.threshold(Long.MAX_VALUE);helper.assertTrue(emitter.threshold()==9999999999L,"original ten-digit cap");
            emitter.threshold(Long.MIN_VALUE);helper.assertTrue(emitter.threshold()==0&&!emitter.emitting(),"original negative clamp");
            emitter.threshold(1234567890L);emitter.lowSignal(false);
            var saved=new CompoundTag();emitter.writeToNBT(saved,level.registryAccess());
            var loaded=new EssentiaLevelEmitterPart(ThaumicEnergistics.LEVEL_EMITTER.get());loaded.readFromNBT(saved,level.registryAccess());
            helper.assertTrue(fire.equals(loaded.filter())&&loaded.threshold()==1234567890L&&!loaded.lowSignal(),"part NBT preserves aspect, long threshold and mode");
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
            try {
                for(long value:List.of(0L,9999999999L,Long.MIN_VALUE,Long.MAX_VALUE)) {
                    var payload=new EmitterThresholdPayload(7,value);EmitterThresholdPayload.CODEC.encode(buffer,payload);
                    helper.assertTrue(payload.equals(EmitterThresholdPayload.CODEC.decode(buffer)),"threshold payload preserves signed long before authoritative clamp");
                }
                emitter.writeToStream(buffer);loaded.readFromStream(buffer);
                helper.assertTrue(emitter.emitting()==loaded.emitting(),"actual part stream preserves rendering state");
            } finally {buffer.release();}
            var recipe=level.getRecipeManager().byKey(ThaumicEnergistics.id("essentia_level_emitter")).orElseThrow().value();
            helper.assertTrue(recipe instanceof thaumcraft.api.crafting.IArcaneRecipe arcane&&arcane.research().equals("thaumicenergistics:teessterm")&&arcane.vis().equals(java.util.Map.of("ignis",4))&&recipe.getResultItem(level.registryAccess()).getCount()==4,"original recipe gives four emitters for four Ignis and retains missing terminal gate");
            for(var pos:List.of(cable,drive,cable.west()))level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            System.out.println("THE_NATIVE_ESSENTIA_EMITTER_WATCHER_PASS");helper.succeed();
        });
    }
}
