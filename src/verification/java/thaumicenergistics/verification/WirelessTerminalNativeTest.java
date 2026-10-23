package thaumicenergistics.verification;

import appeng.api.config.*;
import appeng.api.features.GridLinkables;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.*;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.AEConfig;
import appeng.menu.locator.MenuLocators;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
import thaumcraft.api.aspects.*;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.*;
import thaumicenergistics.storage.*;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class WirelessTerminalNativeTest {
    private static final IActionSource SOURCE=IActionSource.empty();
    private static ItemStack item(String path){return BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:"+path)).getDefaultInstance();}
    private static void near(double actual,double expected,GameTestHelper h,String reason){h.assertTrue(Math.abs(actual-expected)<.00001,reason+" "+actual+" != "+expected);}
    @GameTest(template="empty",timeoutTicks=200)
    public static void wirelessNativeLinkBatteryCadenceModesAndBoundSlot(GameTestHelper h){
        VerificationReports.install();var level=h.getLevel();var anchor=h.absolutePos(new BlockPos(3,3,3));var second=anchor.east(4);var cable=anchor.below();var drive=cable.north();
        for(var pos:List.of(anchor,second)){level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:wireless_access_point")).defaultBlockState());appeng.api.orientation.BlockOrientation.UP_NORTH.setOn(level,pos);}
        for(int x=0;x<=4;x++)PartHelper.setPart(level,cable.east(x),null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        level.setBlockAndUpdate(cable.west(),BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(drive,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState());
        var cell=new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());new EssentiaCell(cell,null).insert(EssentiaKey.of(Aspects.WATER),100,Actionable.MODULATE,SOURCE);level.getCapability(Capabilities.ItemHandler.BLOCK,drive,Direction.UP).insertItem(0,cell,false);
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(second));
        var item=ThaumicEnergistics.WIRELESS_TERMINAL.get();var stack=new ItemStack(item);player.getInventory().setItem(0,stack);
        var locator=MenuLocators.forInventorySlot(0);var link=GridLinkables.get(item);
        h.assertTrue(link!=null&&link.canLink(stack),"approved native AE2 GridLinkables handler registered");link.link(stack,GlobalPos.of(level.dimension(),anchor));item.injectAEPower(stack,500,Actionable.MODULATE);
        h.assertTrue(item.getAEMaxPower(stack)==1600000&&item.isBarVisible(stack)&&!item.isFoil(stack)&&item.getUpgrades(stack).size()==0,"source battery/bar/no glint/no upgrades");
        h.runAfterDelay(50,()->{
            var first=(WirelessAccessPointBlockEntity)level.getBlockEntity(anchor);var nearest=(WirelessAccessPointBlockEntity)level.getBlockEntity(second);
            h.assertTrue(first.isActive()&&nearest.isActive(),"actual AE2 APs active on shared grid");
            var host=item.getMenuHost(player,locator,null);h.assertTrue(host.valid(player)&&host.connectedAccessPoint()==nearest,"approved first search finds nearest active linked-grid AP");
            near(host.distanceMultiplier(),1,h,"source initial transfer multiplier");
            var raw=host.poweredStorage();near(item.getAECurrentPower(stack),500,h,"opening spends no idle charge");
            h.assertTrue(raw.extract(EssentiaKey.of(Aspects.WATER),8,Actionable.SIMULATE,SOURCE)==8,"wireless extraction simulation");near(item.getAECurrentPower(stack),500,h,"simulation retains charge");
            h.assertTrue(raw.extract(EssentiaKey.of(Aspects.WATER),8,Actionable.MODULATE,SOURCE)==8,"wireless extraction");near(item.getAECurrentPower(stack),497.6,h,"wireless transfer billed only to battery");
            h.assertTrue(raw.insert(EssentiaKey.of(Aspects.WATER),8,Actionable.MODULATE,SOURCE)==8,"wireless insertion");near(item.getAECurrentPower(stack),495.2,h,"wireless deposit battery cost");
            var configStack=stack.copy();item.extractAEPower(configStack,Double.MAX_VALUE,Actionable.MODULATE);item.injectAEPower(configStack,1,Actionable.MODULATE);player.getInventory().setItem(1,configStack);
            var configHost=item.getMenuHost(player,MenuLocators.forInventorySlot(1),null);double previousGlobal=thaumicenergistics.items.WirelessEssentiaTerminalItem.GLOBAL_POWER_MULTIPLIER;
            try{
                thaumicenergistics.items.WirelessEssentiaTerminalItem.GLOBAL_POWER_MULTIPLIER=2;
                near(configHost.extractAEPower(1,Actionable.MODULATE,PowerMultiplier.CONFIG),1,h,"legacy redirect ignores nonzero global multiplier");near(item.getAECurrentPower(configStack),0,h,"nonzero global does not multiply battery debit");
                item.injectAEPower(configStack,1,Actionable.MODULATE);thaumicenergistics.items.WirelessEssentiaTerminalItem.GLOBAL_POWER_MULTIPLIER=0;
                near(configHost.extractAEPower(100,Actionable.MODULATE,PowerMultiplier.CONFIG),100,h,"legacy zero-global bypass");near(item.getAECurrentPower(configStack),1,h,"zero-global bypass spends no battery");
                item.extractAEPower(configStack,1,Actionable.MODULATE);h.assertTrue(!configHost.valid(player),"zero-global still requires nonempty battery for connection");
            }finally{thaumicenergistics.items.WirelessEssentiaTerminalItem.GLOBAL_POWER_MULTIPLIER=previousGlobal;player.getInventory().setItem(1,ItemStack.EMPTY);}
            host.workTick(4);host.workTick(4);near(item.getAECurrentPower(stack),495.2,h,"first two four-tick work calls do not drain idle power");
            int dx=second.getX()-(int)player.getX(),dy=second.getY()-(int)player.getY(),dz=second.getZ()-(int)player.getZ();
            host.workTick(4);double factor=AEConfig.instance().wireless_getDrainRate(Math.sqrt(dx*dx+dy*dy+dz*dz));near(item.getAECurrentPower(stack),495.2-13*factor,h,"first idle debit is13 ticks with distance multiplier");
            host.workTick(4);host.workTick(4);host.workTick(4);near(item.getAECurrentPower(stack),495.2-25*factor,h,"later idle debit is12 ticks");
            player.setPos(Vec3.atCenterOf(anchor));h.assertTrue(host.valid(player)&&host.connectedAccessPoint()==nearest,"retain valid previous AP even when another is nearer");
            level.setBlockAndUpdate(second,Blocks.AIR.defaultBlockState());h.assertTrue(host.valid(player)&&host.connectedAccessPoint()==first,"reselect nearest linked AP after previous disappears");
            host.rotateSort(false);host.rotateSort(false);host.rotateView(false);host.rotateView(false);host.rotateView(false);
            h.assertTrue(stack.get(ThaumicEnergistics.TERMINAL_SETTINGS).equals(TerminalSettings.DEFAULT),"wireless saves defaults explicitly unlike legacy cell omission");
            var parsed=ItemStack.parse(level.registryAccess(),stack.save(level.registryAccess())).orElseThrow();h.assertTrue(item.getLinkedPosition(parsed).equals(GlobalPos.of(level.dimension(),anchor)),"native link survives item codec");near(item.getAECurrentPower(parsed),item.getAECurrentPower(stack),h,"battery survives item codec");
            var menu=new EssentiaTerminalMenu(12,player.getInventory(),host);player.containerMenu=menu;menu.broadcastChanges();
            menu.clicked(2,0,ClickType.PICKUP,player);menu.clicked(2,1,ClickType.THROW,player);menu.quickMoveStack(player,2);menu.clicked(11,0,ClickType.SWAP,player);
            h.assertTrue(player.getInventory().getItem(0)==stack&&menu.getCarried().isEmpty(),"bound slot rejects pickup/throw/quick move/number-key swap");
            host.inventory().setItem(0,item("empty_phial"));host.inventory().setItem(1,EssentiaItemApi.configureLabel(item("jar_label"),Aspects.FIRE).orElseThrow());menu.removed(player);menu.removed(player);
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(anchor).inflate(2));h.assertTrue(drops.stream().filter(e->e.getItem().is(item("empty_phial").getItem())).mapToInt(e->e.getItem().getCount()).sum()==1&&drops.stream().filter(e->e.getItem().is(item("jar_label").getItem())).mapToInt(e->e.getItem().getCount()).sum()==1,"wireless private work items drop once on close");drops.forEach(ItemEntity::discard);
            player.setPos(Vec3.atCenterOf(anchor).add(10000,0,0));h.assertTrue(!host.valid(player),"out of range disconnected");player.setPos(Vec3.atCenterOf(anchor));h.assertTrue(host.valid(player),"in-range reconnect");
            player.getInventory().setItem(0,parsed);h.assertTrue(!host.valid(player),"same-type replacement cannot reuse old bound host");
            var replacementHost=item.getMenuHost(player,locator,null);item.extractAEPower(parsed,Double.MAX_VALUE,Actionable.MODULATE);h.assertTrue(!replacementHost.valid(player),"empty battery disconnected");
            player.containerMenu=player.inventoryMenu;
            level.setBlockAndUpdate(anchor,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(cable.west(),Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(drive,Blocks.AIR.defaultBlockState());for(int x=0;x<=4;x++)level.setBlockAndUpdate(cable.east(x),Blocks.AIR.defaultBlockState());
            System.out.println("THE_NATIVE_WIRELESS_TERMINAL_PASS");h.succeed();
        });
    }
    @GameTest(template="empty")
    public static void wirelessFullRequestPowerCheckBeforePartialInsert(GameTestHelper h){
        var calls=new int[1];MEStorage raw=new MEStorage(){
            @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source){calls[0]++;return Math.min(1,amount);}
            @Override public void getAvailableStacks(KeyCounter counts){}
            @Override public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.literal("verification partial storage");}
        };
        var energy=new double[]{1};var storage=new WirelessEssentiaStorage(raw,(amount,mode,multiplier)->{double result=Math.min(energy[0],amount);if(mode==Actionable.MODULATE)energy[0]-=result;return result;});
        h.assertTrue(storage.insert(EssentiaKey.of(Aspects.WATER),8,Actionable.MODULATE,SOURCE)==0&&calls[0]==0&&energy[0]==1,"wireless full8 request denied before raw cell accepts partial1");
        energy[0]=3;h.assertTrue(storage.insert(EssentiaKey.of(Aspects.WATER),8,Actionable.MODULATE,SOURCE)==1,"with full request energy, native partial1 accepted");near(energy[0],2.7,h,"only accepted1 actually billed");
        h.succeed();
    }
}
