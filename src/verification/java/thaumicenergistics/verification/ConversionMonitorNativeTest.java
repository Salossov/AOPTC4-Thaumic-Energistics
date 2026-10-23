package thaumicenergistics.verification;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.*;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.*;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class ConversionMonitorNativeTest {
    private static final IActionSource SOURCE=IActionSource.empty();
    private static ItemStack nativeItem(String name){return BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:"+name)).getDefaultInstance();}
    private static ItemStack phials(Aspect aspect,int count){var stack=EssentiaItemApi.fill(nativeItem("empty_phial"),aspect,8).orElseThrow().resultStack();stack.setCount(count);return stack;}
    private static int inventoryUnits(Player player,Aspect aspect){
        int result=0;for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);var info=EssentiaItemApi.inspect(stack);
            if(info.isPresent()&&info.get().aspect()==aspect)result+=info.get().amount()*stack.getCount();
        }return result;
    }
    @GameTest(template="empty",timeoutTicks=140)
    public static void nativeConversionMonitorFillDepositBatchAndRefusal(GameTestHelper h){
        VerificationReports.install();var level=h.getLevel();var cable=h.absolutePos(new BlockPos(3,2,3));var drive=cable.east();
        level.setBlockAndUpdate(cable.west(),BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(drive,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState());
        level.getCapability(Capabilities.ItemHandler.BLOCK,drive,Direction.UP).insertItem(0,new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get()),false);
        PartHelper.setPart(level,cable,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var part=PartHelper.setPart(level,cable,Direction.SOUTH,null,ThaumicEnergistics.CONVERSION_MONITOR.get());
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(cable));var hit=Vec3.atCenterOf(cable);
        h.runAfterDelay(50,()->{
            h.assertTrue(part.isActive()&&part.getLightLevel()==9,"active conversion monitor has channel and native light");
            var raw=part.getMainNode().getGrid().getStorageService().getInventory();var water=EssentiaKey.of(Aspects.WATER);var fire=EssentiaKey.of(Aspects.FIRE);var earth=EssentiaKey.of(Aspects.EARTH);
            raw.insert(water,24,Actionable.MODULATE,SOURCE);
            part.onUseItemOn(AspectApi.wispEssence(Aspects.WATER),player,InteractionHand.MAIN_HAND,hit);
            part.onUseItemOn(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:nether_quartz_wrench")).getDefaultInstance(),player,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(part.isLocked()&&part.getDisplayed().equals(water),"conversion monitor inherits selection and wrench lock");
            var empties=nativeItem("empty_phial");empties.setCount(2);player.setItemInHand(InteractionHand.MAIN_HAND,empties);
            h.assertTrue(part.onUseItemOn(empties,player,InteractionHand.MAIN_HAND,hit)&&raw.extract(water,100,Actionable.SIMULATE,SOURCE)==16&&inventoryUnits(player,Aspects.WATER)==8,"locked monitor fills exactly one phial for eight stored units");
            player.getInventory().clearContent();var labels=nativeItem("jar_label");labels.setCount(5);player.setItemInHand(InteractionHand.MAIN_HAND,labels);
            h.assertTrue(part.onUseItemOn(labels,player,InteractionHand.MAIN_HAND,hit)&&player.getMainHandItem().getCount()==5&&((IAspectItem)player.getMainHandItem().getItem()).getAspects(player.getMainHandItem()).getAmount(Aspects.WATER)==1&&raw.extract(water,100,Actionable.SIMULATE,SOURCE)==16,"locked label configuration keeps whole stack without network debit");
            var wrong=EssentiaItemApi.fill(nativeItem("void_jar"),Aspects.FIRE,13).orElseThrow().resultStack();player.setItemInHand(InteractionHand.MAIN_HAND,wrong);
            h.assertTrue(!part.onUseItemOn(wrong,player,InteractionHand.MAIN_HAND,hit)&&EssentiaItemApi.inspect(wrong).orElseThrow().amount()==13,"mixed native jar refuses fill");
            player.getInventory().clearContent();player.getInventory().setItem(0,phials(Aspects.WATER,3));player.getInventory().setItem(9,phials(Aspects.WATER,2));player.getInventory().setItem(10,phials(Aspects.FIRE,2));player.setShiftKeyDown(true);
            h.assertTrue(!part.onShiftClicked(player,hit)&&inventoryUnits(player,Aspects.WATER)==40,"modern shift-left callback must not replace original shift-right deposit");
            var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,cable,new net.minecraft.world.phys.BlockHitResult(hit.add(0,0,.501),Direction.SOUTH,cable,false));
            thaumicenergistics.parts.ConversionMonitorInteraction.portableContainerUse(event);
            h.assertTrue(event.getUseBlock()==net.neoforged.neoforge.common.util.TriState.TRUE,"native container shift-right permits block callback");
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
            thaumicenergistics.parts.ConversionMonitorInteraction.portableContainerUse(event);
            h.assertTrue(event.getUseBlock()==net.neoforged.neoforge.common.util.TriState.FALSE,"existing interaction denial remains enforced");
            h.assertTrue(part.onUseItemOn(player.getMainHandItem(),player,InteractionHand.MAIN_HAND,hit)&&raw.extract(water,100,Actionable.SIMULATE,SOURCE)==24&&inventoryUnits(player,Aspects.WATER)==32,"first shift click drains only one held phial");
            h.assertTrue(part.onUseItemOn(player.getMainHandItem(),player,InteractionHand.MAIN_HAND,hit)&&raw.extract(water,100,Actionable.SIMULATE,SOURCE)==56&&inventoryUnits(player,Aspects.WATER)==0&&inventoryUnits(player,Aspects.FIRE)==16,"second shift click sweeps matching aspect only, including other inventory stacks");
            raw.insert(earth,1991,Actionable.MODULATE,SOURCE);player.getInventory().clearContent();var original=phials(Aspects.WATER,1);player.setItemInHand(InteractionHand.MAIN_HAND,original);var before=original.copy();
            h.assertTrue(!part.onUseItemOn(player.getMainHandItem(),player,InteractionHand.MAIN_HAND,hit)&&ItemStack.matches(player.getMainHandItem(),before)&&raw.extract(water,100,Actionable.SIMULATE,SOURCE)==56,"approved fix: one free network unit cannot consume or partially drain an eight-unit phial");
            var jar=EssentiaItemApi.fill(nativeItem("warded_jar"),Aspects.WATER,13).orElseThrow().resultStack();player.setItemInHand(InteractionHand.MAIN_HAND,jar);
            h.assertTrue(part.onUseItemOn(player.getMainHandItem(),player,InteractionHand.MAIN_HAND,hit)&&inventoryUnits(player,Aspects.WATER)==12&&raw.extract(water,100,Actionable.SIMULATE,SOURCE)==57,"jar permits exact one-unit partial deposit without loss");
            player.setShiftKeyDown(false);player.getInventory().clearContent();
            for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            var pair=nativeItem("empty_phial");pair.setCount(2);player.setItemInHand(InteractionHand.MAIN_HAND,pair);
            h.assertTrue(part.onUseItemOn(pair,player,InteractionHand.MAIN_HAND,hit)&&player.getMainHandItem().getCount()==1&&raw.extract(water,100,Actionable.SIMULATE,SOURCE)==49,"full inventory still consumes only one empty phial and eight network units");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(cable).inflate(2));
            h.assertTrue(drops.stream().anyMatch(entity->EssentiaItemApi.inspect(entity.getItem()).map(c->c.aspect()==Aspects.WATER&&c.amount()==8).orElse(false)),"unaccepted filled result drops beside monitor");
            for(var drop:drops)drop.discard();
            var filledPair=phials(Aspects.WATER,2);player.setItemInHand(InteractionHand.MAIN_HAND,filledPair);player.setShiftKeyDown(true);
            h.assertTrue(part.onUseItemOn(filledPair,player,InteractionHand.MAIN_HAND,hit)&&player.getMainHandItem().getCount()==1&&raw.extract(water,100,Actionable.SIMULATE,SOURCE)==57,"full inventory deposit transfers only one whole phial");
            var emptiesDropped=level.getEntitiesOfClass(ItemEntity.class,new AABB(cable).inflate(2));
            h.assertTrue(emptiesDropped.stream().anyMatch(entity->entity.getItem().is(nativeItem("empty_phial").getItem())&&entity.getItem().getCount()==1),"approved fix: unaccepted empty deposit result drops beside monitor");
            for(var drop:emptiesDropped)drop.discard();player.setShiftKeyDown(false);
            var recipe=level.getRecipeManager().byKey(ThaumicEnergistics.id("essentia_conversion_monitor")).orElseThrow().value();
            @SuppressWarnings("unchecked") var crafting=(Recipe<CraftingInput>)recipe;
            var input=CraftingInput.of(3,1,List.of(new ItemStack(ThaumicEnergistics.DIFFUSION_CORE.get()),new ItemStack(ThaumicEnergistics.COALESCENCE_CORE.get()),new ItemStack(ThaumicEnergistics.STORAGE_MONITOR.get())));
            h.assertTrue(crafting.matches(input,level)&&crafting.assemble(input,level.registryAccess()).is(ThaumicEnergistics.CONVERSION_MONITOR.get())&&crafting.assemble(input,level.registryAccess()).getCount()==1,"native recipe matches original three shapeless ingredients and count1");
            for(var pos:List.of(cable,cable.west(),drive))level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            System.out.println("THE_NATIVE_CONVERSION_MONITOR_PASS");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void nativePortableTransfersPowerAndCompensation(GameTestHelper h){
        VerificationReports.install();var cell=new EssentiaCell(new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get()),null);
        var water=EssentiaKey.of(Aspects.WATER);cell.insert(water,7,Actionable.MODULATE,SOURCE);
        var budget=new double[]{100};appeng.api.networking.energy.IEnergySource energy=(n,mode,multiplier)->{double actual=Math.min(n,budget[0]);if(mode==Actionable.MODULATE)budget[0]-=actual;return actual;};
        var powered=new PoweredEssentiaStorage(cell,energy);var empty=nativeItem("empty_phial");
        h.assertTrue(PortableEssentiaTransfers.fill(empty,Aspects.WATER,powered,cell,SOURCE).isEmpty()&&cell.ledger().total()==7&&budget[0]==100,"network with seven units cannot partially fill a phial or consume power");
        cell.insert(water,1,Actionable.MODULATE,SOURCE);budget[0]=2.39;
        h.assertTrue(PortableEssentiaTransfers.fill(empty,Aspects.WATER,powered,cell,SOURCE).isEmpty()&&cell.ledger().total()==8,"finite power refuses complete phial transfer");
        budget[0]=2.4;h.assertTrue(PortableEssentiaTransfers.fill(empty,Aspects.WATER,powered,cell,SOURCE).orElseThrow().actualUnits()==8&&cell.ledger().total()==0&&Math.abs(budget[0])<1e-9,"full eight-unit fill costs exactly 2.4 AE");
        // A storage implementation that shortens its own successful simulation must not duplicate a phial.
        var shortInsert=new appeng.api.storage.MEStorage(){
            @Override public long insert(appeng.api.stacks.AEKey key,long amount,Actionable mode,IActionSource source){return cell.insert(key,mode==Actionable.MODULATE?Math.min(3,amount):amount,mode,source);}
            @Override public long extract(appeng.api.stacks.AEKey key,long amount,Actionable mode,IActionSource source){return cell.extract(key,amount,mode,source);}
            @Override public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.literal("short endpoint");}
        };
        budget[0]=100;var phial=phials(Aspects.WATER,1);var before=phial.copy();
        h.assertTrue(PortableEssentiaTransfers.deposit(phial,null,new PoweredEssentiaStorage(shortInsert,energy),shortInsert,SOURCE).isEmpty()&&cell.ledger().total()==0&&ItemStack.matches(before,phial),"short actual acceptance compensated before consuming native phial");
        System.out.println("THE_NATIVE_PORTABLE_TRANSFERS_PASS");h.succeed();
    }
}
