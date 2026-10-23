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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.AspectApi;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.EssentiaStorageMonitorPart;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaKey;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class MonitorNativeTest {
    @GameTest(template="empty",timeoutTicks=180)
    public static void nativeStorageMonitorSelectionLockAndWatcher(GameTestHelper h){
        VerificationReports.install();var level=h.getLevel();var cable=h.absolutePos(new BlockPos(3,2,3));var drive=cable.east();
        long[] numbers={0,9999,10000,12345,99999,999999,1000000,Long.MAX_VALUE};
        String[] labels={"0","9999","10K","12K","99K","999K","1.0M","9.2E"};
        for(int i=0;i<numbers.length;i++)h.assertTrue(thaumicenergistics.storage.MonitorNumbers.wide(numbers[i]).equals(labels[i]),"rv3 four-character wide number "+numbers[i]);
        level.setBlockAndUpdate(cable.west(),BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(drive,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState());
        level.getCapability(Capabilities.ItemHandler.BLOCK,drive,Direction.UP).insertItem(0,new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get()),false);
        PartHelper.setPart(level,cable,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var monitor=PartHelper.setPart(level,cable,Direction.SOUTH,null,ThaumicEnergistics.STORAGE_MONITOR.get());
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(cable));
        var water=AspectApi.wispEssence(Aspects.WATER);var fire=AspectApi.wispEssence(Aspects.FIRE);
        var wrench=BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:nether_quartz_wrench")).getDefaultInstance();
        var hit=Vec3.atCenterOf(cable);var source=IActionSource.empty();
        h.assertTrue(!monitor.onUseItemOn(water,player,InteractionHand.MAIN_HAND,hit),"unpowered display refuses selection");
        h.runAfterDelay(50,()->{
            h.assertTrue(monitor.isPowered()&&monitor.getLightLevel()==9,"powered channel enables original light9");
            h.assertTrue(monitor.onUseItemOn(water,player,InteractionHand.MAIN_HAND,hit)&&water.getCount()==1,"wisp config selects aspect without consumption");
            h.assertTrue(monitor.getDisplayed().equals(EssentiaKey.of(Aspects.WATER))&&monitor.getAmount()==0,"empty selected aspect remains a valid zero display");
            var inventory=monitor.getMainNode().getGrid().getStorageService().getInventory();
            h.assertTrue(inventory.insert(EssentiaKey.of(Aspects.WATER),1234,Actionable.MODULATE,source)==1234,"real drive accepts monitored essentia");
            h.assertTrue(monitor.onUseItemOn(wrench,player,InteractionHand.MAIN_HAND,hit)&&monitor.isLocked(),"wrench locks monitor");
            h.assertTrue(!monitor.onUseWithoutItem(player,hit)&&!monitor.onUseItemOn(fire,player,InteractionHand.MAIN_HAND,hit),"locked aspect cannot clear or change");
        });
        h.runAfterDelay(60,()->{
            h.assertTrue(monitor.getAmount()==1234,"actual AE2 watcher synchronizes stored total");
            var saved=new CompoundTag();monitor.writeToNBT(saved,level.registryAccess());
            h.assertTrue(saved.getBoolean("Locked")&&saved.getString("TrackedAspect").equals("aqua"),"release NBT field names preserved");
            var copy=new EssentiaStorageMonitorPart(ThaumicEnergistics.STORAGE_MONITOR.get());copy.readFromNBT(saved,level.registryAccess());
            h.assertTrue(copy.isLocked()&&copy.getDisplayed().equals(monitor.getDisplayed())&&copy.getAmount()==0,"save retains lock/aspect and recomputes transient amount");
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
            try{monitor.writeToStream(buffer);copy.readFromStream(buffer);h.assertTrue(copy.isLocked()&&copy.getAmount()==1234&&copy.getDisplayed().equals(monitor.getDisplayed()),"client stream includes lock/aspect/long amount");}finally{buffer.release();}
            monitor.getMainNode().getGrid().getStorageService().getInventory().extract(EssentiaKey.of(Aspects.WATER),234,Actionable.MODULATE,source);
        });
        h.runAfterDelay(70,()->{
            h.assertTrue(monitor.getAmount()==1000,"actual extraction updates display amount");
            monitor.onUseItemOn(wrench,player,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(!monitor.isLocked()&&!monitor.onUseItemOn(new ItemStack(Items.DIAMOND),player,InteractionHand.MAIN_HAND,hit),"unrelated item does not clear unlocked selection");
            h.assertTrue(monitor.onUseWithoutItem(player,hit)&&monitor.getDisplayed()==null&&monitor.getAmount()==0,"empty hand clears unlocked display");
            var empty=new CompoundTag();monitor.writeToNBT(empty,level.registryAccess());h.assertTrue(!empty.contains("Locked")&&!empty.contains("TrackedAspect"),"default settings omitted as in original");
            var recipe=level.getRecipeManager().byKey(ThaumicEnergistics.id("essentia_storage_monitor")).orElseThrow().value();
            h.assertTrue(recipe.getType()==net.minecraft.world.item.crafting.RecipeType.CRAFTING&&recipe.getResultItem(level.registryAccess()).is(ThaumicEnergistics.STORAGE_MONITOR.get()),"ordinary shapeless monitor recipe is registered");
            @SuppressWarnings("unchecked") var crafting=(net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.CraftingInput>)recipe;
            for(var panel:List.of("monitor","semi_dark_monitor","dark_monitor")){
                var input=net.minecraft.world.item.crafting.CraftingInput.of(2,1,List.of(new ItemStack(ThaumicEnergistics.LEVEL_EMITTER.get()),BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:"+panel)).getDefaultInstance()));
                h.assertTrue(crafting.matches(input,level)&&crafting.assemble(input,level.registryAccess()).getCount()==1,"original illuminated-panel group includes "+panel);
            }
            for(var p:List.of(cable,drive,cable.west()))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
            System.out.println("THE_NATIVE_STORAGE_MONITOR_PASS");h.succeed();
        });
    }
}
