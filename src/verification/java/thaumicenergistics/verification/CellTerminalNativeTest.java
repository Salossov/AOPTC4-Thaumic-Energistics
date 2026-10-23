package thaumicenergistics.verification;

import appeng.api.config.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.*;
import appeng.blockentity.storage.MEChestBlockEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.*;
import thaumcraft.api.aspects.*;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.*;
import thaumicenergistics.storage.*;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class CellTerminalNativeTest {
    private static final IActionSource SOURCE=IActionSource.empty();
    private static ItemStack item(String path){return BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:"+path)).getDefaultInstance();}
    @GameTest(template="empty",timeoutTicks=180)
    public static void nativeChestCellTerminalIsolationModesAndClose(GameTestHelper h){
        VerificationReports.install();var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,2,3));var cable=pos.east();var drive=pos.east(2);
        level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:chest")).defaultBlockState());
        h.assertTrue(level.getBlockEntity(pos) instanceof MEChestBlockEntity,"native ME Chest fixture");
        var chest=(MEChestBlockEntity)level.getBlockEntity(pos);
        var cell=new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());new EssentiaCell(cell,null).insert(EssentiaKey.of(Aspects.WATER),32,Actionable.MODULATE,SOURCE);chest.setCell(cell);
        level.setBlockAndUpdate(pos.west(),BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(drive,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState());
        var remote=new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());new EssentiaCell(remote,null).insert(EssentiaKey.of(Aspects.WATER),1000,Actionable.MODULATE,SOURCE);
        level.getCapability(Capabilities.ItemHandler.BLOCK,drive,Direction.UP).insertItem(0,remote,false);
        PartHelper.setPart(level,cable,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(pos));
        h.runAfterDelay(50,()->{
            h.assertTrue(chest.isPowered(),"chest uses actual AE2 power");
            var host=new CellTerminalHost(chest);
            h.assertTrue(host.valid(player)&&host.entries().size()==1&&host.entries().getFirst().amount()==32&&!host.entries().getFirst().craftable(),"cell terminal advertises only its local32, not network1032");
            var grid=chest.getActionableNode().getGrid();grid.getCraftingService().addGlobalCraftingProvider(TerminalCraftingFixture.provider(level));
            h.assertTrue(!host.supportsCrafting()&&host.entries().stream().noneMatch(TerminalEntry::craftable),"local cell never exposes grid craftables");
            var menu=new EssentiaTerminalMenu(11,player.getInventory(),host);player.containerMenu=menu;
            h.assertTrue(menu.action(player,11,0,"aqua")&&!menu.action(player,11,6,"ignis"),"cell selection works but crafting launch denied");
            menu.setCarried(item("empty_phial"));
            h.assertTrue(menu.action(player,11,1,"aqua")&&EssentiaItemApi.inspect(menu.getCarried()).orElseThrow().amount()==8,"same original cursor transfer fills native phial");
            h.assertTrue(new EssentiaCell(chest.getCell(),null).ledger().total()==24&&new EssentiaCell(level.getCapability(Capabilities.ItemHandler.BLOCK,drive,Direction.UP).getStackInSlot(0),null).ledger().total()==1000,"cell operation cannot spend remote network essentia");
            menu.setCarried(ItemStack.EMPTY);
            host.rotateSort(false);h.assertTrue(host.sort()==1&&chest.getCell().get(ThaumicEnergistics.TERMINAL_SETTINGS).sort()==1,"nondefault sort stored on actual cell");
            host.rotateSort(false);h.assertTrue(host.sort()==0&&new CellTerminalHost(chest).sort()==1,"release omitted default write persists old nondefault on reopen");
            host.rotateView(false);host.rotateView(false);host.rotateView(false);
            h.assertTrue(host.view()==ViewItems.ALL&&new CellTerminalHost(chest).view()==ViewItems.CRAFTABLE,"release view default omission also preserved");
            var parsed=ItemStack.parse(level.registryAccess(),chest.getCell().save(level.registryAccess())).orElseThrow();
            h.assertTrue(parsed.get(ThaumicEnergistics.TERMINAL_SETTINGS).equals(new TerminalSettings(1,2))&&new EssentiaCell(parsed,null).ledger().total()==24,"item component codec saves modes without changing essentia contents");
            host.inventory().setItem(0,item("empty_phial"));host.inventory().setItem(1,EssentiaItemApi.configureLabel(item("jar_label"),Aspects.FIRE).orElseThrow());
            menu.removed(player);menu.removed(player);
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));
            h.assertTrue(host.inventory().isEmpty()&&drops.stream().filter(e->e.getItem().is(item("empty_phial").getItem())).mapToInt(e->e.getItem().getCount()).sum()==1&&drops.stream().filter(e->e.getItem().is(item("jar_label").getItem())).mapToInt(e->e.getItem().getCount()).sum()==1,"private work items drop exactly once when cell menu closes");
            drops.forEach(ItemEntity::discard);
            var replacement=new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K4).get());new EssentiaCell(replacement,null).insert(EssentiaKey.of(Aspects.FIRE),99,Actionable.MODULATE,SOURCE);chest.setCell(replacement);
            h.assertTrue(!host.valid(player)&&!menu.action(player,11,1,"ignis")&&new EssentiaCell(chest.getCell(),null).ledger().total()==99,"cell replacement invalidates the old host before any operation");
            var denied=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.501,0),chest.getTop(),pos,false));denied.setUseBlock(TriState.FALSE);
            CellTerminalInteraction.openCell(denied);h.assertTrue(!denied.isCanceled()&&denied.getUseBlock()==TriState.FALSE,"protection denial not overridden");
            var unpoweredPos=pos.south(3);level.setBlockAndUpdate(unpoweredPos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:chest")).defaultBlockState());
            var unpowered=(MEChestBlockEntity)level.getBlockEntity(unpoweredPos);unpowered.setCell(parsed.copy());
            var unpoweredHost=new CellTerminalHost(unpowered);var unpoweredMenu=new EssentiaTerminalMenu(12,player.getInventory(),unpoweredHost);player.containerMenu=unpoweredMenu;unpoweredMenu.setCarried(item("empty_phial"));
            h.assertTrue(unpoweredHost.valid(player)&&unpoweredHost.entries().isEmpty()&&!unpoweredMenu.action(player,12,1,"aqua")&&new EssentiaCell(unpowered.getCell(),null).ledger().total()==24,"unpowered chest hides contents and refuses transfers without spending its cell");
            unpoweredMenu.setCarried(ItemStack.EMPTY);unpoweredMenu.removed(player);
            player.containerMenu=player.inventoryMenu;
            for(var at:List.of(pos,pos.west(),cable,drive,unpoweredPos))level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
            System.out.println("THE_NATIVE_CELL_TERMINAL_PASS");h.succeed();
        });
    }
}
