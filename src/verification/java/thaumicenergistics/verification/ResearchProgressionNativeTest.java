package thaumicenergistics.verification;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.crafting.WorkbenchApi;
import thaumcraft.api.research.ResearchApi;
import thaumcraft.api.wands.WandApi;
import thaumicenergistics.ThaumicEnergistics;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class ResearchProgressionNativeTest {
    private static ItemStack item(String id) {
        var key=ResourceLocation.parse(id);
        if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalStateException("Missing fixture item "+id);
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void nativeImportedResearchAndArcaneGate(GameTestHelper helper)throws Exception {
        VerificationReports.install();
        var cookie=CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"THEProgression"),false);
        var player=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        player.connection=new ServerGamePacketListenerImpl(player.server,new Connection(PacketFlow.SERVERBOUND),player,cookie) {
            @Override public void send(Packet<?> packet) {}
        };
        try {
            var definitions=ResearchApi.definitions(player);
            var root=definitions.get("thaumicenergistics:teresearch");
            var cores=definitions.get("thaumicenergistics:tecores");
            var storage=definitions.get("thaumicenergistics:testorage");
            var buses=definitions.get("thaumicenergistics:teio");
            var terminal=definitions.get("thaumicenergistics:teessterm");
            helper.assertTrue(terminal!=null&&terminal.secondary()&&terminal.concealed()&&terminal.complexity()==1&&terminal.column()==-1&&terminal.row()==-4&&terminal.pageOrder().size()==9&&terminal.parents().equals(List.of("thaumicenergistics:tecores"))&&terminal.aspects().equals(Map.of("permutatio",5,"sensus",5,"potentia",3,"aqua",3)),"original secondary monitoring research costs, parent and nine default-enabled pages");
            helper.assertTrue(!terminal.available(ResearchApi.knowledge(player)),"monitoring cannot bypass conversion cores");
            helper.assertTrue(root!=null&&root.autoUnlock()&&root.round()&&root.column()==0&&root.row()==0,"original root is round and auto-unlocked");
            helper.assertTrue(cores!=null&&cores.aspects().equals(Map.of("limus",3,"praecantatio",5,"machina",5,"permutatio",5))&&cores.complexity()==2,"original core aspect cost and complexity");
            helper.assertTrue(storage!=null&&storage.pageOrder().size()==8&&storage.recipeGroups().size()==3&&storage.recipePages().size()==14,"storage keeps original eight pages and three grouped variant pages");
            helper.assertTrue(buses!=null&&buses.concealed()&&buses.parents().equals(List.of(cores.key(),"thaumicenergistics:pseudo_tubefilter")),"concealed buses keep core and native filter dependencies");
            helper.assertTrue(!ResearchApi.exists(player,"thaumicenergistics:tevisint"),"Vis is not unlocked by replacing missing arcane-terminal research");
            if(!ResearchApi.knows(player,root.key()))ResearchApi.grant(player,root.key());
            helper.assertTrue(!cores.available(ResearchApi.knowledge(player))&&!storage.available(ResearchApi.knowledge(player)),"new knowledge cannot bypass distillation");
            ResearchApi.grant(player,"DISTILESSENTIA");
            helper.assertTrue(ResearchApi.knows(player,"thaumicenergistics:pseudo_distilessentia")&&cores.available(ResearchApi.knowledge(player)),"native completion mirrors actual addon alias and exposes cores");
            helper.assertTrue(!storage.available(ResearchApi.knowledge(player)),"storage still requires original warded arcana parent");
            ResearchApi.grant(player,"WARDEDARCANA");
            helper.assertTrue(storage.available(ResearchApi.knowledge(player)),"both original storage prerequisites are required");
            var table=new SimpleContainer(11);
            var wand=item("thaumcraft:iron_capped_wooden_wand");
            for(var aspect:Aspects.PRIMALS)WandApi.set(wand,aspect,2000);
            table.setItem(10,wand);
            table.setItem(0,item("thaumcraft:quicksilver_drop"));
            table.setItem(1,item("thaumcraft:quicksilver_drop"));
            table.setItem(2,item("thaumcraft:quicksilver_drop"));
            table.setItem(3,item("thaumcraft:order_shard"));
            table.setItem(4,item("ae2:formation_core"));
            var menu=WorkbenchApi.menuClass().getConstructor(int.class,Inventory.class,Container.class).newInstance(1,player.getInventory(),table);
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty()&&!menu.getSlot(0).mayPickup(player),"native arcane result stays locked before research completion despite charged wand and matching ingredients");
            int water=WandApi.stored(wand,Aspects.WATER);
            helper.assertTrue(menu.getSlot(0).remove(1).isEmpty()&&WandApi.stored(wand,Aspects.WATER)==water&&table.getItem(0).getCount()==1,"rejected craft consumes neither vis nor ingredient");
            ResearchApi.grant(player,cores.key());
            menu.broadcastChanges();
            helper.assertTrue(menu.getSlot(0).getItem().is(ThaumicEnergistics.COALESCENCE_CORE.get())&&menu.getSlot(0).mayPickup(player),"real native arcane menu exposes addon output after matching research completion");
            var output=menu.getSlot(0).remove(1);
            helper.assertTrue(output.is(ThaumicEnergistics.COALESCENCE_CORE.get())&&table.getItem(0).isEmpty()&&WandApi.stored(wand,Aspects.WATER)<water,"native menu crafts and debits vis/ingredients");
            helper.assertTrue(!buses.available(ResearchApi.knowledge(player)),"cores alone cannot expose buses before native tube filter");
            ResearchApi.grant(player,"TUBEFILTER");
            helper.assertTrue(buses.available(ResearchApi.knowledge(player))&&ResearchApi.knows(player,"thaumicenergistics:pseudo_tubefilter"),"filter completion opens original concealed bus research");
            player.getInventory().setItem(0,item("thaumcraft:thaumonomicon"));
            player.getInventory().setItem(1,item("minecraft:paper"));
            player.getInventory().setItem(2,item("thaumcraft:scribing_tools"));
            var bookType=Class.forName("thaumcraft.menu.ThaumonomiconMenu");
            var book=(AbstractContainerMenu)bookType.getConstructor(int.class,Inventory.class,UUID.class).newInstance(2,player.getInventory(),UUID.randomUUID());
            helper.assertTrue((Boolean)bookType.getMethod("createNote",ServerPlayer.class,String.class).invoke(book,player,buses.key()),"ordinary book action creates an addon bus research note");
            helper.assertTrue(!player.getInventory().contains(item("minecraft:paper"))&&player.getInventory().items.stream().anyMatch(stack->stack.is(item("thaumcraft:research_notes").getItem()))&&!ResearchApi.knows(player,buses.key()),"note creation replaces consumed paper with research notes but does not grant research");
            for(int slot=0;slot<10;slot++)table.setItem(slot,ItemStack.EMPTY);
            int ingredient=0;for(String id:List.of("ae2:monitor","thaumicenergistics:diffusion_core","thaumicenergistics:coalescence_core","ae2:logic_processor","thaumcraft:vis_filter"))table.setItem(ingredient++,item(id));
            menu.broadcastChanges();helper.assertTrue(menu.getSlot(0).getItem().isEmpty(),"terminal arcane output remains gated before monitoring purchase");
            helper.assertTrue(!(Boolean)bookType.getMethod("createNote",ServerPlayer.class,String.class).invoke(book,player,terminal.key()),"secondary research refuses missing aspect pools");
            for(var cost:terminal.aspects().entrySet())ResearchApi.completeAspect(player,thaumcraft.api.aspects.Aspect.getAspect(cost.getKey()),cost.getValue().shortValue());
            helper.assertTrue((Boolean)bookType.getMethod("createNote",ServerPlayer.class,String.class).invoke(book,player,terminal.key())&&ResearchApi.knows(player,terminal.key()),"native book purchases secondary monitoring research without paper");
            for(var tag:terminal.aspects().keySet())helper.assertTrue(ResearchApi.knowledge(player).aspectPools().get(tag)==0,"native purchase debits exact original aspect cost "+tag);
            menu.broadcastChanges();helper.assertTrue(menu.getSlot(0).getItem().is(ThaumicEnergistics.ESSENTIA_TERMINAL.get())&&menu.getSlot(0).mayPickup(player),"original terminal recipe becomes craftable after proper secondary completion");
            var terminalResult=menu.getSlot(0).remove(1);helper.assertTrue(terminalResult.is(ThaumicEnergistics.ESSENTIA_TERMINAL.get())&&table.getItem(0).isEmpty(),"native arcane terminal craft consumes ingredients");
            System.out.println("THE_NATIVE_IMPORTED_RESEARCH_ARCANE_GATE_PASS nodes=5 links=3 note_created=true monitoring_purchased=true terminal_crafted=true puzzle_completion_not_tested=true");
            helper.succeed();
        } finally {player.connection=null;}
    }
}
