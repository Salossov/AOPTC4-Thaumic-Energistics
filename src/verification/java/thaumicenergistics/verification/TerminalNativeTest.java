package thaumicenergistics.verification;

import appeng.api.config.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
import thaumcraft.api.aspects.*;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaTerminalMenu;
import thaumicenergistics.network.*;
import thaumicenergistics.parts.EssentiaTerminalPart;
import thaumicenergistics.storage.*;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class TerminalNativeTest {
    private static final IActionSource SOURCE=IActionSource.empty();
    private static ItemStack item(String id){return BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:"+id)).getDefaultInstance();}
    @GameTest(template="empty",timeoutTicks=80)
    public static void nativeTerminalWorkBudgetAndOutputPreflight(GameTestHelper h){
        VerificationReports.install();var inventory=new TerminalInventory(()->{});var cell=new EssentiaCell(new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get()),null);
        var water=EssentiaKey.of(Aspects.WATER);cell.insert(water,200,Actionable.MODULATE,SOURCE);var budget=new double[]{10000};
        var powered=new PoweredEssentiaStorage(cell,(requested,mode,multiplier)->{double n=Math.min(requested,budget[0]);if(mode==Actionable.MODULATE)budget[0]-=n;return n;});
        var empty=item("empty_phial");empty.setCount(16);inventory.setItem(0,empty);
        h.assertTrue(inventory.process(Aspects.WATER,powered,cell,SOURCE)==64&&inventory.getItem(0).getCount()==8&&inventory.getItem(1).getCount()==8&&cell.ledger().total()==136,"one work cycle fills eight whole phials for64units");
        inventory.getItem(1).setCount(64);var originalInput=inventory.getItem(0).copy();double before=budget[0];
        h.assertTrue(inventory.process(Aspects.WATER,powered,cell,SOURCE)==0&&ItemStack.matches(inventory.getItem(0),originalInput)&&cell.ledger().total()==136&&budget[0]==before,"full output refuses transfer before item/resource/energy mutation");
        inventory.setItem(1,EssentiaItemApi.fill(item("empty_phial"),Aspects.FIRE,8).orElseThrow().resultStack());
        h.assertTrue(inventory.process(Aspects.WATER,powered,cell,SOURCE)==0&&cell.ledger().total()==136,"incompatible output preflight does not consume network contents");
        inventory.clearContent();var labels=item("jar_label");labels.setCount(64);inventory.setItem(0,labels);
        h.assertTrue(inventory.process(Aspects.WATER,powered,cell,SOURCE)==64&&inventory.getItem(0).getCount()==32&&inventory.getItem(1).getCount()==32&&cell.ledger().total()==136,"release label simulation also counts:32labels/cycle without essentia debit");
        inventory.clearContent();var jar=EssentiaItemApi.fill(item("void_jar"),Aspects.WATER,13).orElseThrow().resultStack();inventory.setItem(0,jar);
        h.assertTrue(inventory.process(Aspects.FIRE,powered,cell,SOURCE)==13&&EssentiaItemApi.inspect(inventory.getItem(1)).orElseThrow().amount()==0&&cell.ledger().total()==149,"nonempty container always drains its own aspect regardless of selected widget");
        inventory.clearContent();cell.insert(EssentiaKey.of(Aspects.EARTH),1898,Actionable.MODULATE,SOURCE);
        var phial=EssentiaItemApi.fill(item("empty_phial"),Aspects.WATER,8).orElseThrow().resultStack();inventory.setItem(0,phial);before=budget[0];
        h.assertTrue(inventory.process(null,powered,cell,SOURCE)==0&&inventory.getItem(1).isEmpty()&&ItemStack.matches(inventory.getItem(0),phial)&&cell.ledger().total()==2047&&budget[0]==before,"terminal simulation already prevents original partial-phial loss; no mutation for one free unit");
        h.assertTrue(MonitorNumbers.slim(999).equals("999")&&MonitorNumbers.slim(1000).equals("1K")&&MonitorNumbers.slim(12345).equals("12K")&&MonitorNumbers.slim(1000000).equals("1M"),"original three-character slim formatting");
        var entries=List.of(new TerminalEntry(water,100,false),new TerminalEntry(EssentiaKey.of(Aspects.FIRE),0,true),new TerminalEntry(EssentiaKey.of(Aspects.EARTH),5,true));
        h.assertTrue(TerminalEntries.visible(entries,0,ViewItems.CRAFTABLE,"").size()==2&&TerminalEntries.visible(entries,0,ViewItems.STORED,"").size()==2&&TerminalEntries.visible(entries,0,ViewItems.ALL,"  AQUA ").equals(List.of(entries.get(0))),"original stored/craftable/search filters");
        h.assertTrue(TerminalEntries.visible(entries,1,ViewItems.ALL,"").getFirst().key().equals(water),"amount order descends with name tie break");
        System.out.println("THE_NATIVE_TERMINAL_WORK_PASS");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180)
    public static void nativeTerminalMenuCadencePersistenceAndCraftables(GameTestHelper h){
        VerificationReports.install();var level=h.getLevel();var cable=h.absolutePos(new BlockPos(3,2,3));var drive=cable.east();
        level.setBlockAndUpdate(cable.west(),BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(drive,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState());
        level.getCapability(Capabilities.ItemHandler.BLOCK,drive,Direction.UP).insertItem(0,new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get()),false);
        PartHelper.setPart(level,cable,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var part=PartHelper.setPart(level,cable,Direction.SOUTH,null,ThaumicEnergistics.ESSENTIA_TERMINAL.get());
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(cable));
        h.runAfterDelay(50,()->{
            h.assertTrue(part.isActive()&&part.getLightLevel()==9,"terminal native channel/light9");
            var raw=part.getMainNode().getGrid().getStorageService().getInventory();raw.insert(EssentiaKey.of(Aspects.WATER),200,Actionable.MODULATE,SOURCE);
            var menu=new EssentiaTerminalMenu(7,player.getInventory(),part);player.containerMenu=menu;
            h.assertTrue(menu.slots.size()==38&&menu.getSlot(0).x==8&&menu.getSlot(0).y==92&&menu.getSlot(1).x==26&&menu.getSlot(2).y==180&&menu.getSlot(11).y==122,"original slots and hotbar-before-main ordering");
            h.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.STONE))&&menu.getSlot(0).mayPlace(item("jar_label"))&&!menu.getSlot(1).mayPlace(item("empty_phial")),"input accepts only native containers/labels; output forbids insertion");
            var input=item("empty_phial");input.setCount(9);part.inventory().setItem(0,input);
            h.assertTrue(menu.action(player,7,0,"aqua")&&!menu.action(player,8,0,"ignis")&&!menu.action(player,7,0,"not_an_aspect"),"owned current-menu actions reject wrong ID and unknown aspect");
            for(int i=0;i<3;i++)menu.broadcastChanges();h.assertTrue(part.inventory().getItem(1).isEmpty(),"three menu ticks do no work");
            menu.broadcastChanges();h.assertTrue(part.inventory().getItem(1).getCount()==8&&part.inventory().getItem(0).getCount()==1,"fourth menu tick performs original64-unit cycle");
            h.assertTrue(menu.action(player,7,2,"")&&menu.action(player,7,5,"")&&part.sort()==1&&part.view()==ViewItems.CRAFTABLE,"sorting and backward view cycling retained");
            var wrench=BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:nether_quartz_wrench")).getDefaultInstance();
            part.onUseItemOn(wrench,player,InteractionHand.MAIN_HAND,Vec3.atCenterOf(cable));part.onUseItemOn(wrench,player,InteractionHand.MAIN_HAND,Vec3.atCenterOf(cable));
            var saved=new CompoundTag();part.writeToNBT(saved,level.registryAccess());
            var copy=new EssentiaTerminalPart(ThaumicEnergistics.ESSENTIA_TERMINAL.get());copy.readFromNBT(saved,level.registryAccess());
            h.assertTrue(saved.getByte("partRotation")==3&&copy.sort()==1&&copy.view()==ViewItems.CRAFTABLE&&copy.selected()==null&&ItemStack.matches(copy.inventory().getItem(1),part.inventory().getItem(1)),"world NBT keeps modes/inventory/legacy rotation, selection remains transient");
            var drops=new ArrayList<ItemStack>();part.addPartDrop(drops,true);part.addAdditionalDrops(drops,true);
            h.assertTrue(drops.size()==1&&drops.getFirst().is(ThaumicEnergistics.ESSENTIA_TERMINAL.get())&&drops.getFirst().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().contains("slots"),"AE2 wrench item contains work inventory once without separate contents drops");
            drops.clear();part.addAdditionalDrops(drops,false);h.assertTrue(drops.size()==2,"ordinary break exposes both retained work-slot items");
            var grid=part.getMainNode().getGrid();grid.getCraftingService().addGlobalCraftingProvider(TerminalCraftingFixture.provider(level));
            h.assertTrue(grid.getCraftingService().isCraftable(EssentiaKey.of(Aspects.FIRE)),"native generic processing pattern advertises essentia craftable key");
            var recipe=level.getRecipeManager().byKey(ThaumicEnergistics.id("essentia_terminal")).orElseThrow().value();
            h.assertTrue(recipe.getResultItem(level.registryAccess()).is(ThaumicEnergistics.ESSENTIA_TERMINAL.get())&&!recipe.getType().equals(net.minecraft.world.item.crafting.RecipeType.CRAFTING),"original terminal is arcane and cannot use ordinary crafting table");
            h.assertTrue(recipe instanceof thaumcraft.api.crafting.IArcaneRecipe arcane&&arcane.research().equals("thaumicenergistics:teessterm")&&arcane.vis().equals(Map.of("aqua",5,"ordo",2,"ignis",1)),"original terminal research and5Water/2Order/1Fire whole-vis costs");
            @SuppressWarnings("unchecked") var crafting=(net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.CraftingInput>)recipe;
            for(String panel:List.of("monitor","semi_dark_monitor","dark_monitor")){
                var ingredients=List.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:"+panel)).getDefaultInstance(),new ItemStack(ThaumicEnergistics.DIFFUSION_CORE.get()),new ItemStack(ThaumicEnergistics.COALESCENCE_CORE.get()),BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:logic_processor")).getDefaultInstance(),item("vis_filter"));
                h.assertTrue(crafting.matches(net.minecraft.world.item.crafting.CraftingInput.of(5,1,ingredients),level)&&crafting.assemble(net.minecraft.world.item.crafting.CraftingInput.of(5,1,ingredients),level.registryAccess()).getCount()==1,"all original illuminated panel variants and five ingredients "+panel);
            }
            player.containerMenu=player.inventoryMenu;
        });
        h.runAfterDelay(60,()->{
            h.assertTrue(part.entries().stream().anyMatch(e->e.key().equals(EssentiaKey.of(Aspects.FIRE))&&e.amount()==0&&e.craftable())&&part.entries().stream().anyMatch(e->e.key().equals(EssentiaKey.of(Aspects.WATER))&&e.amount()==136),"terminal list combines actual stored and craftable-only keys");
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
            try{
                var state=new TerminalStatePayload(7,1,2,"aqua",List.of(new TerminalEntry(EssentiaKey.of(Aspects.WATER),Long.MAX_VALUE,true)));
                TerminalStatePayload.CODEC.encode(buffer,state);h.assertTrue(TerminalStatePayload.CODEC.decode(buffer).equals(state),"state packet preserves exact long amounts and crafting flags");
                var action=new TerminalActionPayload(7,1,"aqua");TerminalActionPayload.CODEC.encode(buffer,action);h.assertTrue(TerminalActionPayload.CODEC.decode(buffer).equals(action),"bounded action packet round trip");
            }finally{buffer.release();}
            for(var pos:List.of(cable,cable.west(),drive))level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            System.out.println("THE_NATIVE_TERMINAL_MENU_PASS");h.succeed();
        });
    }
}
