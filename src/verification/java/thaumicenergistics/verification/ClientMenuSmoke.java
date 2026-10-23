package thaumicenergistics.verification;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import thaumcraft.api.aspects.AspectApi;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumcraft.api.research.ResearchApi;
import thaumcraft.api.research.ResearchLinks;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.client.CellWorkbenchScreen;
import thaumicenergistics.client.EssentiaBusScreen;
import thaumicenergistics.client.EssentiaLevelEmitterScreen;
import thaumicenergistics.client.StorageBusScreen;
import thaumicenergistics.client.StoragePriorityScreen;
import thaumicenergistics.menu.CellWorkbenchMenu;
import thaumicenergistics.menu.EssentiaBusMenu;
import thaumicenergistics.menu.EssentiaLevelEmitterMenu;
import thaumicenergistics.menu.StorageBusMenu;
import thaumicenergistics.menu.StoragePriorityMenu;
import thaumicenergistics.parts.EssentiaIOBusPart;
import thaumicenergistics.parts.EssentiaLevelEmitterPart;
import thaumicenergistics.parts.EssentiaStorageBusPart;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaCell;
import thaumicenergistics.storage.EssentiaKey;

/** Verification source set only: real client screens, packets and framebuffer captures.
 * Uses no desktop input and mutates only a marked, isolated verification save. */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, value = Dist.CLIENT)
public final class ClientMenuSmoke {
    private static final String SAVE = "THE-client-menu-smoke";
    private static final BlockPos CABLE = new BlockPos(3,100,3), BENCH = new BlockPos(5,100,3);
    private static final BlockPos VIS_INPUT = new BlockPos(3,100,12), VIS_OUTPUT = new BlockPos(3,100,14);
    private static final BlockPos CONVERSION = new BlockPos(16,104,9);
    private static final BlockPos TERMINAL = new BlockPos(20,104,16);
    private static final BlockPos CELL_CHEST = new BlockPos(28,104,16);
    private static final BlockPos WIRELESS_AP = new BlockPos(36,104,16);
    private static final AtomicReference<Throwable> SERVER_ERROR = new AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicBoolean VIS_LINK_DRAINED = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean TERMINAL_WRENCHED = new java.util.concurrent.atomic.AtomicBoolean();
    private static final Map<String,Object> EVIDENCE = new LinkedHashMap<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static final long RUN = System.currentTimeMillis();
    private static int step, age, totalTicks;
    private static boolean queued, finished, terminalOpenQueued;
    private static String requestedCapture;
    private static Path awaitingCapture;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("thaumicenergistics.clientSmoke") || finished) return;
        var mc = Minecraft.getInstance();
        try {
            long window=mc.getWindow().getWindow();
            if (!Boolean.getBoolean("thaumicenergistics.hiddenWindowHook") || !Boolean.getBoolean("thaumicenergistics.silentSoundHook"))
                throw new IllegalStateException("Hidden/silent verification hooks were not installed");
            if (org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_VISIBLE)!=0
                    || org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_FOCUSED)!=0)
                throw new IllegalStateException("Verification window must stay hidden and unfocused");
            EVIDENCE.put("hidden_unfocused_window",true); EVIDENCE.put("sound_playback_blocked",true);
            if (++totalTicks > 6000) throw new IllegalStateException("Client smoke timeout; screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
            if (totalTicks % 200 == 0) System.out.println("THE_CLIENT_SMOKE_STATE step=" + step + " screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
            if (SERVER_ERROR.get() != null) throw new IllegalStateException("Server fixture/action failed",SERVER_ERROR.get());
            if (awaitingCapture != null) {
                if (Files.isRegularFile(awaitingCapture) && Files.size(awaitingCapture) > 100) {
                    CAPTURES.add(awaitingCapture.toAbsolutePath().toString()); awaitingCapture = null; advance();
                }
                return;
            }
            if (mc.level == null || mc.player == null || mc.gameMode == null) {
                if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
                        && Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve(".thaumicenergistics-smoke-owned"))) {
                    for (var child : mc.screen.children()) if (child instanceof Button button && button.active
                            && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                            && contents.getKey().equals("selectWorld.backupJoinConfirmButton")) {
                        EVIDENCE.put("owned_test_save_backup_confirmed",true); button.onPress(); break;
                    }
                }
                // Only the isolated quick-play launch enables this harness. Accept the
                // vanilla experimental-settings confirmation for this newly made test save.
                if (mc.screen != null && mc.screen.getClass().getSimpleName().contains("ConfirmExperimental")) {
                    for (var child : mc.screen.children()) if (child instanceof Button button && button.active
                            && (button.getMessage().getString().equals("Proceed") || button.getMessage().getString().equals("I know what I'm doing!"))) {
                        button.onPress(); break;
                    }
                }
                return;
            }
            if (++age < 12) return;
            switch (step) {
                case 0 -> {
                    if (!queued) { queued = true; server(ClientMenuSmoke::fixture); }
                }
                case 1 -> {
                    if (mc.screen instanceof StorageBusScreen && mc.player.containerMenu instanceof StorageBusMenu menu && menu.hasNetworkTool()) capture("storage-main");
                }
                case 2 -> { click(10,ClickType.PICKUP); advance(); }
                case 3 -> {
                    if (!mc.player.containerMenu.getCarried().isEmpty()) { click(4,ClickType.PICKUP); advance(); }
                }
                case 4 -> {
                    if (mc.player.containerMenu instanceof StorageBusMenu menu && EssentiaKey.of(Aspects.WATER).equals(menu.filter(4))) {
                        EVIDENCE.put("server_cursor_filter_packet",true); capture("storage-filter");
                    }
                }
                case 5 -> { click(10,ClickType.PICKUP); click(11,ClickType.QUICK_MOVE); advance(); }
                case 6 -> {
                    if (mc.player.containerMenu.getSlot(9).getItem().is(ae("inverter_card").getItem())) {
                        click(11,ClickType.QUICK_MOVE); button(StorageBusMenu.VOID); advance();
                    }
                }
                case 7 -> {
                    if (mc.player.containerMenu instanceof StorageBusMenu menu && menu.allowVoid() && !menu.getSlot(46).getItem().isEmpty()) {
                        if(!EssentiaKey.of(Aspects.WATER).equals(menu.filter(4))) throw new IllegalStateException("Storage filter lost after inverter insertion");
                        EVIDENCE.put("storage_filter_retained_after_inverter",true);
                        EVIDENCE.put("inverter_tool_and_void_packets",true); capture("storage-inverter-void");
                    }
                }
                case 8 -> { button(StorageBusMenu.PRIORITY); advance(); }
                case 9 -> { if (mc.screen instanceof StoragePriorityScreen) { button(3); advance(); } }
                case 10 -> {
                    if (mc.player.containerMenu instanceof StoragePriorityMenu menu && menu.priority() == 100) capture("priority-delta");
                }
                case 11 -> {
                    var field = editBox(); field.setValue("");
                    for (char c : "-1234567".toCharArray()) field.charTyped(c,0);
                    advance();
                }
                case 12 -> {
                    if (mc.player.containerMenu instanceof StoragePriorityMenu menu && menu.priority() == -1234567 && editBox().getValue().equals("-1234567")) {
                        EVIDENCE.put("absolute_priority_real_payload",true); capture("priority-negative");
                    }
                }
                case 13 -> { editBox().keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,0,0); advance(); }
                case 14 -> {
                    if (mc.player.containerMenu instanceof StoragePriorityMenu menu && menu.priority() == -123456) {
                        EVIDENCE.put("backspace_at_typing_limit",true); button(0); advance();
                    }
                }
                case 15 -> {
                    if (mc.screen instanceof StorageBusScreen) { server(player -> ((EssentiaIOBusPart)PartHelper.getPartHost(player.level(),CABLE).getPart(Direction.EAST)).onUseWithoutItem(player,Vec3.atCenterOf(CABLE))); advance(); }
                }
                case 16 -> { if (mc.screen instanceof EssentiaBusScreen && mc.player.containerMenu instanceof EssentiaBusMenu menu && menu.importing()) { click(15,ClickType.QUICK_MOVE); advance(); } }
                case 17 -> {
                    if (mc.player.containerMenu instanceof EssentiaBusMenu menu && menu.filterEnabled(0)) {
                        EVIDENCE.put("io_capacity_card_packet",true); capture("import-capacity");
                    }
                }
                case 18 -> { server(player -> ((EssentiaIOBusPart)PartHelper.getPartHost(player.level(),CABLE).getPart(Direction.WEST)).onUseWithoutItem(player,Vec3.atCenterOf(CABLE))); advance(); }
                case 19 -> {
                    if (mc.screen instanceof EssentiaBusScreen && mc.player.containerMenu instanceof EssentiaBusMenu menu && !menu.importing()) capture("export-menu");
                }
                case 20 -> {
                    server(player -> player.level().getBlockState(BENCH).useWithoutItem(player.level(),player,new BlockHitResult(Vec3.atCenterOf(BENCH),Direction.UP,BENCH,false)));
                    advance();
                }
                case 21 -> { if (mc.screen instanceof CellWorkbenchScreen) { click(67,ClickType.QUICK_MOVE); advance(); } }
                case 22 -> {
                    if (mc.player.containerMenu instanceof CellWorkbenchMenu menu && !menu.getSlot(CellWorkbenchMenu.CELL_SLOT).getItem().isEmpty()) {
                        button(CellWorkbenchMenu.PARTITION_CONTENTS); advance();
                    }
                }
                case 23 -> {
                    if (mc.player.containerMenu instanceof CellWorkbenchMenu menu && menu.filter(0) != null && menu.filter(1) != null) {
                        EVIDENCE.put("workbench_real_cell_and_partition_packets",true); capture("workbench-partitions");
                    }
                }
                case 24 -> { mc.player.closeContainer(); advance(); }
                case 25 -> { if (mc.screen == null) capture("world-fixture"); }
                case 26 -> { server(ClientMenuSmoke::visFixture); advance(); }
                case 27 -> {
                    server(player -> {
                        var host=PartHelper.getPartHost(player.level(),VIS_OUTPUT);
                        if(host!=null && host.getPart(Direction.SOUTH) instanceof thaumicenergistics.parts.VisInterfacePart part && part.isActive()) {
                            var input=(thaumicenergistics.parts.VisInterfacePart)PartHelper.getPartHost(player.level(),VIS_INPUT).getPart(Direction.WEST);
                            if(!input.isActive()) return;
                            if(!part.isVisProvider()) {
                                var card=ae("memory_card"); player.setShiftKeyDown(true); input.onUseItemOn(card,player,InteractionHand.MAIN_HAND,Vec3.ZERO);
                                player.setShiftKeyDown(false); part.onUseItemOn(card,player,InteractionHand.MAIN_HAND,Vec3.ZERO);
                                if(!part.isVisProvider()) throw new IllegalStateException("Actual Vis memory-card link failed after grid boot");
                            }
                            if(part.consumeVis(Aspects.WATER,1)>0) VIS_LINK_DRAINED.set(true);
                        }
                    });
                    var host=PartHelper.getPartHost(mc.level,VIS_OUTPUT);
                    if(VIS_LINK_DRAINED.get() && host!=null && host.getPart(Direction.SOUTH) instanceof thaumicenergistics.parts.VisInterfacePart part && part.drainColor()==Aspects.WATER.getColor()) {
                        EVIDENCE.put("vis_drain_color_real_client_sync",true); capture("vis-interface-water");
                    }
                }
                case 28 -> { mc.setScreen(new AspectPaletteScreen()); advance(); }
                case 29 -> {
                    if(mc.screen instanceof AspectPaletteScreen palette) {
                        EVIDENCE.put("aspect_palette",palette.samples()); EVIDENCE.put("gui_scale",mc.getWindow().getGuiScale());
                        EVIDENCE.put("aspect_palette_rendered",true); capture("aspect-palette");
                    }
                }
                case 30 -> {
                    mc.setScreen(null);
                    server(player -> {
                        ResearchApi.grant(player,ResearchNativeTest.TARGET);
                        var book=BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:thaumonomicon")).getDefaultInstance();
                        player.getInventory().setItem(8,book);player.getInventory().selected=8;player.inventoryMenu.broadcastChanges();
                        book.use(player.level(),player,InteractionHand.MAIN_HAND);
                    });advance();
                }
                case 31 -> {
                    if(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> book
                            &&mc.screen.getClass().getSimpleName().equals("ThaumonomiconScreen")
                            &&ResearchApi.clientKnowledge().completedResearch().contains(ResearchNativeTest.ALIAS)) {
                        var definitions=ResearchApi.definitions();var alias=definitions.get(ResearchNativeTest.ALIAS);var target=definitions.get(ResearchNativeTest.TARGET);
                        if(alias==null||target==null||!alias.icon().equals(target.icon())||!alias.pageOrder().equals(target.pageOrder())
                                ||!ResearchLinks.target(alias,definitions).key().equals(target.key()))throw new IllegalStateException("Native client alias display/catalog sync differs from target");
                        var known=ResearchApi.clientKnowledge().completedResearch();
                        var categories=ResearchApi.books().categories().values().stream().filter(c->c.requiredResearch().isEmpty()||known.contains(c.requiredResearch()))
                                .sorted(java.util.Comparator.comparingInt(thaumcraft.api.book.BookCategory::order).thenComparing(thaumcraft.api.book.BookCategory::id)).toList();
                        int index=-1;for(int i=0;i<categories.size();i++)if(categories.get(i).id().equals(alias.category()))index=i;
                        if(index<0||index>=book.getYSize()/24)throw new IllegalStateException("Verification category must fit visible native tabs");
                        mc.screen.mouseClicked(book.getGuiLeft()-12,book.getGuiTop()+index*24+12,0);
                        EVIDENCE.put("linked_research_real_client_sync",true);advance();
                    }
                }
                case 32 -> capture("linked-research-map");
                case 33 -> {
                    if(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> book) {
                        mc.screen.mouseClicked(book.getGuiLeft()+book.getXSize()/2,book.getGuiTop()+book.getYSize()/2-7,0);advance();
                    }
                }
                case 34 -> capture("linked-research-pages");
                case 35 -> {
                    mc.setScreen(null);
                    server(player -> {
                        for(var key:List.of("DISTILESSENTIA","WARDEDARCANA","TUBEFILTER","thaumicenergistics:teresearch","thaumicenergistics:tecores","thaumicenergistics:testorage","thaumicenergistics:teio"))
                            if(!ResearchApi.knows(player,key))ResearchApi.grant(player,key);
                        player.getMainHandItem().use(player.level(),player,InteractionHand.MAIN_HAND);
                    });advance();
                }
                case 36 -> {
                    if(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> book
                            &&mc.screen.getClass().getSimpleName().equals("ThaumonomiconScreen")
                            &&ResearchApi.clientKnowledge().completedResearch().contains("thaumicenergistics:testorage")) {
                        var storage=ResearchApi.definitions().get("thaumicenergistics:testorage");
                        if(storage==null||storage.pageOrder().size()!=8||storage.recipeGroups().size()!=3)throw new IllegalStateException("Original storage pages lost in client sync");
                        if(!net.minecraft.client.resources.language.I18n.exists("tc.research_name."+storage.key()))throw new IllegalStateException("Missing namespaced research translation");
                        var known=ResearchApi.clientKnowledge().completedResearch();
                        var categories=ResearchApi.books().categories().values().stream().filter(category->category.requiredResearch().isEmpty()||known.contains(category.requiredResearch()))
                                .sorted(java.util.Comparator.comparingInt(thaumcraft.api.book.BookCategory::order).thenComparing(thaumcraft.api.book.BookCategory::id)).toList();
                        int index=-1;for(int categoryIndex=0;categoryIndex<categories.size();categoryIndex++)if(categories.get(categoryIndex).id().equals(storage.category()))index=categoryIndex;
                        if(index<0||index>=book.getYSize()/24)throw new IllegalStateException("Addon category does not fit native tabs");
                        mc.screen.mouseClicked(book.getGuiLeft()-12,book.getGuiTop()+index*24+12,0);
                        EVIDENCE.put("imported_research_real_client_sync",true);advance();
                    }
                }
                case 37 -> capture("original-research-map");
                case 38 -> {
                    if(mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("ThaumonomiconScreen")) {
                        var open=mc.screen.getClass().getDeclaredMethod("openResearch",String.class);open.setAccessible(true);open.invoke(mc.screen,"thaumicenergistics:testorage");
                        var selected=mc.screen.getClass().getDeclaredField("selected");selected.setAccessible(true);
                        if(!(selected.get(mc.screen) instanceof thaumcraft.api.research.ResearchDefinition research)||!research.key().equals("thaumicenergistics:testorage"))throw new IllegalStateException("Native book did not open original storage research");
                        advance();
                    }
                }
                case 39 -> capture("original-storage-research-text");
                case 40 -> {
                    var pages=mc.screen.getClass().getDeclaredMethod("pages");pages.setAccessible(true);
                    var resolved=(List<?>)pages.invoke(mc.screen);int recipeIndex=-1;
                    for(int index=0;index<resolved.size();index++) {
                        var recipe=resolved.get(index).getClass().getDeclaredMethod("recipe");recipe.setAccessible(true);
                        if(!((String)recipe.invoke(resolved.get(index))).isEmpty()){recipeIndex=index;break;}
                    }
                    if(recipeIndex<0)throw new IllegalStateException("Native book resolved no storage recipe pages");
                    var spread=mc.screen.getClass().getDeclaredField("spread");spread.setAccessible(true);spread.setInt(mc.screen,recipeIndex/2);advance();
                }
                case 41 -> capture("original-storage-research-recipes");
                case 42 -> {
                    mc.setScreen(null);
                    server(player -> {
                        var emitter=PartHelper.setPart(player.serverLevel(),CABLE,Direction.UP,null,ThaumicEnergistics.LEVEL_EMITTER.get());
                        if(emitter==null)throw new IllegalStateException("Emitter placement failed");
                        player.teleportTo(player.serverLevel(),4.5,100,4.5,135,29);
                    });advance();
                }
                case 43 -> {
                    server(player -> {
                        if(PartHelper.getPartHost(player.level(),CABLE).getPart(Direction.UP) instanceof EssentiaLevelEmitterPart emitter&&emitter.isActive())
                            emitter.onUseWithoutItem(player,Vec3.atCenterOf(CABLE));
                    });
                    if(mc.screen instanceof EssentiaLevelEmitterScreen)advance();
                }
                case 44 -> { click(10,ClickType.PICKUP); advance(); }
                case 45 -> { if(!mc.player.containerMenu.getCarried().isEmpty()){click(0,ClickType.PICKUP);click(10,ClickType.PICKUP);advance();} }
                case 46 -> {
                    if(mc.player.containerMenu instanceof EssentiaLevelEmitterMenu menu&&EssentiaKey.of(Aspects.WATER).equals(menu.filter())) {
                        var field=editBox();field.setValue("");for(char c:"7".toCharArray())field.charTyped(c,0);advance();
                    }
                }
                case 47 -> {
                    var part=emitter(mc);
                    if(mc.player.containerMenu instanceof EssentiaLevelEmitterMenu menu&&menu.threshold()==7&&editBox().getValue().equals("7")&&part!=null&&part.emitting()) {
                        EVIDENCE.put("emitter_menu_real_packets",true);capture("emitter-menu-high");
                    }
                }
                case 48 -> { button(6); advance(); }
                case 49 -> {
                    var part=emitter(mc);
                    if(mc.player.containerMenu instanceof EssentiaLevelEmitterMenu menu&&menu.lowSignal()&&part!=null&&!part.emitting())capture("emitter-menu-low");
                }
                case 50 -> { button(6); mc.player.closeContainer(); advance(); }
                case 51 -> {
                    var part=emitter(mc);
                    if(mc.screen==null&&part!=null&&part.emitting()) {EVIDENCE.put("emitter_world_flare_synced",true);capture("emitter-world-active");}
                }
                case 52 -> {
                    server(player -> {
                        var level=player.serverLevel();var pos=new BlockPos(12,104,3);
                        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(pos.west(),block("ae2:creative_energy_cell"));
                        level.setBlockAndUpdate(pos.east(),block("ae2:drive"));
                        var cell=new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
                        new EssentiaCell(cell,null).insert(EssentiaKey.of(Aspects.WATER),1234,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
                        var slot=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,pos.east(),Direction.UP);
                        if(!slot.getStackInSlot(0).isEmpty())slot.extractItem(0,1,false);
                        slot.insertItem(0,cell,false);
                        PartHelper.setPart(level,pos,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
                        // Six faces on separate vertically stacked hosts; energy uses the west side.
                        for(int i=0;i<6;i++){
                            var at=pos.above(i*3);
                            if(i>0){level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(at.west(),Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(at.east(),Blocks.AIR.defaultBlockState());}
                            level.setBlockAndUpdate(Direction.values()[i]==Direction.WEST?at.east():at.west(),block("ae2:creative_energy_cell"));
                            if(i>0)PartHelper.setPart(level,at,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
                            var part=PartHelper.setPart(level,at,Direction.values()[i],null,ThaumicEnergistics.STORAGE_MONITOR.get());
                            if(part==null)throw new IllegalStateException("Monitor placement failed "+i);
                        }
                        player.setNoGravity(true);player.getAbilities().flying=true;player.onUpdateAbilities();
                    });advance();
                }
                case 53 -> {
                    server(player -> {
                        var base=new BlockPos(12,104,3);
                        for(int i=0;i<6;i++) {
                            var part=(thaumicenergistics.parts.EssentiaStorageMonitorPart)PartHelper.getPartHost(player.level(),base.above(i*3)).getPart(Direction.values()[i]);
                            if(!part.isActive())return;
                        }
                        for(int i=0;i<6;i++) {
                            var part=(thaumicenergistics.parts.EssentiaStorageMonitorPart)PartHelper.getPartHost(player.level(),base.above(i*3)).getPart(Direction.values()[i]);
                            part.onUseItemOn(AspectApi.wispEssence(Aspects.WATER),player,InteractionHand.MAIN_HAND,Vec3.atCenterOf(base));
                            if(i==0)part.onUseItemOn(ae("nether_quartz_wrench"),player,InteractionHand.MAIN_HAND,Vec3.atCenterOf(base));
                        }
                    });advance();
                }
                case 54,56,58,60,62,64 -> {
                    int i=(step-54)/2;var face=Direction.values()[i];var pos=new BlockPos(12,104+i*3,3);
                    server(player -> {
                        var eye=Vec3.atCenterOf(pos).add(face.getStepX()*2.4,face.getStepY()*2.4,face.getStepZ()*2.4);
                        float yaw=switch(face){case NORTH->0;case SOUTH->180;case EAST->90;case WEST->-90;default->0;};
                        float pitch=face==Direction.UP?90:face==Direction.DOWN?-90:0;
                        player.teleportTo(player.serverLevel(),eye.x,eye.y-player.getEyeHeight(),eye.z,yaw,pitch);
                    });advance();
                }
                case 55,57,59,61,63,65 -> {
                    int i=(step-55)/2;var face=Direction.values()[i];var pos=new BlockPos(12,104+i*3,3);
                    var host=PartHelper.getPartHost(mc.level,pos);
                    if(host!=null&&host.getPart(face) instanceof thaumicenergistics.parts.EssentiaStorageMonitorPart part&&part.isActive()&&EssentiaKey.of(Aspects.WATER).equals(part.getDisplayed())){
                        if(i==0&&(!part.isLocked()||part.getAmount()!=1234))throw new IllegalStateException("Monitor client lock/count differs from real drive");
                        EVIDENCE.put("monitor_face_"+face.getName(),true);capture("monitor-world-"+face.getName());
                    }
                }
                case 66 -> {
                    server(player -> {
                        var level=player.serverLevel();
                        level.setBlockAndUpdate(CONVERSION,Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(CONVERSION.west(),block("ae2:creative_energy_cell"));
                        level.setBlockAndUpdate(CONVERSION.east(),block("ae2:drive"));
                        var cell=new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
                        new EssentiaCell(cell,null).insert(EssentiaKey.of(Aspects.WATER),24,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
                        var slots=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,CONVERSION.east(),Direction.UP);
                        if(!slots.getStackInSlot(0).isEmpty())slots.extractItem(0,1,false);slots.insertItem(0,cell,false);
                        PartHelper.setPart(level,CONVERSION,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
                        PartHelper.setPart(level,CONVERSION,Direction.SOUTH,null,ThaumicEnergistics.CONVERSION_MONITOR.get());
                        player.setGameMode(GameType.SURVIVAL);player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();
                        player.getInventory().clearContent();player.getInventory().selected=0;
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
                        var empty=nativeItem("empty_phial");empty.setCount(2);player.setItemInHand(InteractionHand.MAIN_HAND,empty);player.inventoryMenu.broadcastChanges();
                        player.teleportTo(level,16.5,104.5-player.getEyeHeight(),11.9,180,0);
                    });advance();
                }
                case 67 -> {
                    var part=conversion(mc);
                    if(part!=null&&part.isActive()){
                        server(player -> {
                            var serverPart=(thaumicenergistics.parts.EssentiaConversionMonitorPart)PartHelper.getPartHost(player.level(),CONVERSION).getPart(Direction.SOUTH);
                            serverPart.onUseItemOn(AspectApi.wispEssence(Aspects.WATER),player,InteractionHand.MAIN_HAND,Vec3.atCenterOf(CONVERSION));
                            serverPart.onUseItemOn(ae("nether_quartz_wrench"),player,InteractionHand.MAIN_HAND,Vec3.atCenterOf(CONVERSION));
                        });advance();
                    }
                }
                case 68 -> {
                    var part=conversion(mc);
                    if(part!=null&&part.isLocked()&&part.getAmount()==24&&mc.player.getMainHandItem().is(nativeItem("empty_phial").getItem())){
                        conversionUse(mc);advance();
                    }
                }
                case 69 -> {
                    var part=conversion(mc);
                    if(part!=null&&part.getAmount()==16&&portableUnits(mc.player,Aspects.WATER)==8){
                        EVIDENCE.put("conversion_fill_real_packet",true);capture("conversion-monitor-filled-phial");
                    }
                }
                case 70 -> {
                    server(player -> {
                        // Shift descends in flight; keep the real packet fixture within reach
                        // while crouching through both clicks of the forty-tick window.
                        player.getAbilities().flying=false;player.onUpdateAbilities();player.setNoGravity(true);
                        player.getInventory().clearContent();player.getInventory().selected=0;
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
                        player.getInventory().setItem(0,phials(Aspects.WATER,2));player.getInventory().setItem(9,phials(Aspects.WATER,2));
                        player.getInventory().setItem(10,phials(Aspects.FIRE,1));player.inventoryMenu.broadcastChanges();
                    });mc.options.keyShift.setDown(true);advance();
                }
                case 71 -> {
                    if(mc.player.isShiftKeyDown()&&portableUnits(mc.player,Aspects.WATER)==32&&mc.player.getMainHandItem().getCount()==2){conversionUse(mc);advance();}
                }
                case 72 -> {
                    var part=conversion(mc);
                    if(part!=null&&part.getAmount()==24&&portableUnits(mc.player,Aspects.WATER)==24){conversionUse(mc);advance();}
                }
                case 73 -> {
                    var part=conversion(mc);
                    if(part!=null&&part.getAmount()==48&&portableUnits(mc.player,Aspects.WATER)==0&&portableUnits(mc.player,Aspects.FIRE)==8){
                        EVIDENCE.put("conversion_batch_real_packets",true);capture("conversion-monitor-batch-deposit");
                    }
                }
                case 74 -> {
                    server(player -> {
                        var part=(thaumicenergistics.parts.EssentiaConversionMonitorPart)PartHelper.getPartHost(player.level(),CONVERSION).getPart(Direction.SOUTH);
                        long accepted=part.getMainNode().getGrid().getStorageService().getInventory().insert(EssentiaKey.of(Aspects.EARTH),1999,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
                        if(accepted!=1999)throw new IllegalStateException("One-free-unit conversion fixture invalid");
                        player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,phials(Aspects.WATER,1));player.inventoryMenu.broadcastChanges();
                    });advance();
                }
                case 75 -> {
                    if(mc.player.isShiftKeyDown()&&mc.player.getMainHandItem().getCount()==1&&portableUnits(mc.player,Aspects.WATER)==8){conversionUse(mc);advance();}
                }
                case 76 -> {
                    var part=conversion(mc);
                    if(age>=25&&part!=null&&part.getAmount()==48&&mc.player.getMainHandItem().getCount()==1&&portableUnits(mc.player,Aspects.WATER)==8){
                        EVIDENCE.put("conversion_partial_phial_refused",true);capture("conversion-monitor-phial-refused");
                    }
                }
                case 77 -> {
                    mc.options.keyShift.setDown(false);
                    server(player->{
                        var level=player.serverLevel();level.setBlockAndUpdate(TERMINAL,Blocks.AIR.defaultBlockState());
                        // Removing a prior restored terminal legitimately drops its work items.
                        // Clear only this owned fixture's local drops before seeding inventory.
                        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(TERMINAL).inflate(4)).forEach(net.minecraft.world.entity.Entity::discard);
                        level.setBlockAndUpdate(TERMINAL.west(),block("ae2:creative_energy_cell"));level.setBlockAndUpdate(TERMINAL.east(),block("ae2:drive"));
                        var cell=new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());var contents=new EssentiaCell(cell,null);
                        contents.insert(EssentiaKey.of(Aspects.WATER),200,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
                        contents.insert(EssentiaKey.of(Aspects.EARTH),5,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
                        contents.insert(EssentiaKey.of(thaumcraft.api.aspects.Aspect.getAspect("praecantatio")),7,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
                        var slots=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,TERMINAL.east(),Direction.UP);
                        if(!slots.getStackInSlot(0).isEmpty())slots.extractItem(0,1,false);slots.insertItem(0,cell,false);
                        PartHelper.setPart(level,TERMINAL,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
                        PartHelper.setPart(level,TERMINAL,Direction.SOUTH,null,ThaumicEnergistics.ESSENTIA_TERMINAL.get());
                        player.getInventory().clearContent();player.getInventory().selected=0;player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
                        var empty=nativeItem("empty_phial");empty.setCount(16);player.getInventory().setItem(9,empty);
                        var labels=nativeItem("jar_label");labels.setCount(64);player.getInventory().setItem(10,labels);
                        player.inventoryMenu.broadcastChanges();player.teleportTo(level,20.5,104.5-player.getEyeHeight(),18.9,180,0);
                    });advance();
                }
                case 78 -> {
                    var host=PartHelper.getPartHost(mc.level,TERMINAL);
                    if(host!=null&&host.getPart(Direction.SOUTH) instanceof thaumicenergistics.parts.EssentiaTerminalPart part&&part.isActive()){
                        server(player->{var partServer=(thaumicenergistics.parts.EssentiaTerminalPart)PartHelper.getPartHost(player.level(),TERMINAL).getPart(Direction.SOUTH);partServer.getMainNode().getGrid().getCraftingService().addGlobalCraftingProvider(TerminalCraftingFixture.provider(player.level()));});
                        capture("terminal-world-active");
                    }
                }
                case 79 -> {
                    if(!terminalOpenQueued){terminalOpenQueued=true;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(TERMINAL).add(0,0,.501),Direction.SOUTH,TERMINAL,false));}
                    if(mc.screen instanceof thaumicenergistics.client.EssentiaTerminalScreen&&mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.entries().size()==4){EVIDENCE.put("terminal_menu_real_packets",true);capture("terminal-main");}
                }
                case 80 -> {terminalClick(Aspects.WATER,0);advance();}
                case 81 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.selected()==Aspects.WATER)capture("terminal-selected-water");
                }
                case 82 -> {click(11,ClickType.PICKUP);click(0,ClickType.PICKUP);advance();}
                case 83 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().getCount()==16&&terminalAmount(menu,Aspects.WATER)==72)capture("terminal-work-filled");
                }
                case 84 -> {click(1,ClickType.QUICK_MOVE);advance();}
                case 85 -> {if(mc.player.containerMenu.getSlot(1).getItem().isEmpty()){click(12,ClickType.PICKUP);click(0,ClickType.PICKUP);advance();}}
                case 86 -> {
                    if(mc.player.containerMenu.getSlot(1).getItem().getCount()==64&&mc.player.containerMenu.getSlot(0).getItem().isEmpty()){
                        EVIDENCE.put("terminal_work_slots",true);click(1,ClickType.QUICK_MOVE);advance();
                    }
                }
                case 87 -> {server(player->{var empty=nativeItem("empty_phial");empty.setCount(2);player.getInventory().setItem(12,empty);player.containerMenu.broadcastChanges();});advance();}
                case 88 -> {if(mc.player.containerMenu.getSlot(14).getItem().getCount()==2){click(14,ClickType.PICKUP);advance();}}
                case 89 -> {if(mc.player.containerMenu.getCarried().getCount()==2){terminalClick(Aspects.WATER,0);advance();}}
                case 90 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.getCarried().getCount()==1&&terminalAmount(menu,Aspects.WATER)==64){
                        EVIDENCE.put("terminal_cursor_transfer",true);click(14,ClickType.PICKUP);advance();
                    }
                }
                case 91 -> {
                    var screen=(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.screen;
                    mc.screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+17,0);
                    mc.screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+37,0);mc.screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+37,0);advance();
                }
                case 92 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.sort()==1&&menu.view()==appeng.api.config.ViewItems.CRAFTABLE)capture("terminal-craftable");
                }
                case 93 -> {terminalClick(Aspects.FIRE,0);advance();}
                case 94 -> {
                    if(mc.player.containerMenu instanceof appeng.menu.me.crafting.CraftAmountMenu menu&&menu.getWhatToCraft()!=null&&menu.getWhatToCraft().what().equals(EssentiaKey.of(Aspects.FIRE)))capture("terminal-native-craft-amount");
                }
                case 95 -> {((appeng.menu.me.crafting.CraftAmountMenu)mc.player.containerMenu).confirm(0,false,false);advance();}
                case 96 -> {
                    if(mc.screen instanceof thaumicenergistics.client.EssentiaTerminalScreen&&mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu){
                        EVIDENCE.put("terminal_native_craft_menu",true);var screen=(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.screen;
                        mc.screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+37,0);advance();
                    }
                }
                case 97 -> {editBox().setValue(" AQUA ");advance();}
                case 98 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.view()==appeng.api.config.ViewItems.ALL){
                        var filtered=thaumicenergistics.storage.TerminalEntries.visible(menu.entries(),menu.sort(),menu.view(),editBox().getValue());
                        if(filtered.size()==1&&filtered.getFirst().key().equals(EssentiaKey.of(Aspects.WATER))){EVIDENCE.put("terminal_search_filter",true);capture("terminal-search");}
                    }
                }
                case 99 -> {
                    mc.player.closeContainer();
                    server(player->{
                        var part=(thaumicenergistics.parts.EssentiaTerminalPart)PartHelper.getPartHost(player.level(),TERMINAL).getPart(Direction.SOUTH);
                        var empty=nativeItem("empty_phial");empty.setCount(2);part.inventory().setItem(0,empty);
                        part.inventory().setItem(1,thaumcraft.api.aspects.EssentiaItemApi.configureLabel(nativeItem("jar_label"),Aspects.WATER).orElseThrow());
                        player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,ae("nether_quartz_wrench"));player.inventoryMenu.broadcastChanges();
                    });mc.options.keyShift.setDown(true);advance();
                }
                case 100 -> {
                    if(mc.screen==null&&mc.player.isShiftKeyDown()&&mc.player.getMainHandItem().is(ae("nether_quartz_wrench").getItem())){
                        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(TERMINAL).add(0,0,.501),Direction.SOUTH,TERMINAL,false));advance();
                    }
                }
                case 101 -> {
                    var host=PartHelper.getPartHost(mc.level,TERMINAL);
                    if(host!=null&&host.getPart(Direction.SOUTH)==null){
                        server(player->{
                            var found=new ArrayList<ItemStack>();
                            for(int slot=0;slot<36;slot++){
                                var item=player.getInventory().getItem(slot);
                                if(item.is(ThaumicEnergistics.ESSENTIA_TERMINAL.get())){found.add(item.copy());player.getInventory().setItem(slot,ItemStack.EMPTY);}
                            }
                            for(var entity:player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(TERMINAL).inflate(3))){
                                if(entity.getItem().is(ThaumicEnergistics.ESSENTIA_TERMINAL.get())){found.add(entity.getItem().copy());entity.discard();}
                                else throw new IllegalStateException("Wrench duplicated terminal work items: "+entity.getItem());
                            }
                            if(found.size()!=1||found.getFirst().getCount()!=1)throw new IllegalStateException("Wrench expected one terminal item, got "+found);
                            player.setItemInHand(InteractionHand.MAIN_HAND,found.getFirst());player.inventoryMenu.broadcastChanges();TERMINAL_WRENCHED.set(true);
                        });mc.options.keyShift.setDown(false);advance();
                    }
                }
                case 102 -> {
                    if(TERMINAL_WRENCHED.get()&&!mc.player.isShiftKeyDown()&&mc.player.getMainHandItem().is(ThaumicEnergistics.ESSENTIA_TERMINAL.get())){
                        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(TERMINAL).add(0,0,.501),Direction.SOUTH,TERMINAL,false));advance();
                    }
                }
                case 103 -> {
                    var host=PartHelper.getPartHost(mc.level,TERMINAL);
                    if(host!=null&&host.getPart(Direction.SOUTH) instanceof thaumicenergistics.parts.EssentiaTerminalPart&&mc.player.getMainHandItem().isEmpty()){
                        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(TERMINAL).add(0,0,.501),Direction.SOUTH,TERMINAL,false));advance();
                    }
                }
                case 104 -> {
                    if(mc.screen instanceof thaumicenergistics.client.EssentiaTerminalScreen&&mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu
                            &&menu.getSlot(0).getItem().getCount()==2&&menu.getSlot(1).getItem().is(nativeItem("jar_label").getItem())&&menu.sort()==1&&menu.selected()==null){
                        EVIDENCE.put("terminal_real_wrench_restore",true);capture("terminal-wrench-restored");
                    }
                }
                case 105 -> {
                    mc.player.closeContainer();
                    server(player->{
                        var level=player.serverLevel();
                        for(var pos:List.of(CELL_CHEST,CELL_CHEST.east(),CELL_CHEST.east(2),CELL_CHEST.west()))level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(CELL_CHEST).inflate(4)).forEach(net.minecraft.world.entity.Entity::discard);
                        level.setBlockAndUpdate(CELL_CHEST,block("ae2:chest"));
                        level.setBlockAndUpdate(CELL_CHEST.west(),block("ae2:creative_energy_cell"));
                        level.setBlockAndUpdate(CELL_CHEST.east(2),block("ae2:drive"));
                        PartHelper.setPart(level,CELL_CHEST.east(),null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
                        var local=new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
                        new EssentiaCell(local,null).insert(EssentiaKey.of(Aspects.WATER),64,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
                        ((appeng.blockentity.storage.MEChestBlockEntity)level.getBlockEntity(CELL_CHEST)).setCell(local);
                        var remote=new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
                        new EssentiaCell(remote,null).insert(EssentiaKey.of(Aspects.WATER),1000,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
                        level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,CELL_CHEST.east(2),Direction.UP).insertItem(0,remote,false);
                        player.getInventory().clearContent();player.getInventory().selected=0;player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
                        var empty=nativeItem("empty_phial");empty.setCount(4);player.getInventory().setItem(9,empty);player.inventoryMenu.broadcastChanges();
                        player.teleportTo(level,28.5,106.6,16.5,0,90);
                    });advance();
                }
                case 106 -> {
                    if(mc.screen==null&&mc.level.getBlockEntity(CELL_CHEST) instanceof appeng.blockentity.storage.MEChestBlockEntity chest&&chest.isPowered()){
                        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(CELL_CHEST).add(0,0,.501),Direction.SOUTH,CELL_CHEST,false));advance();
                    }
                }
                case 107 -> {
                    if(mc.player.containerMenu instanceof appeng.menu.implementations.MEChestMenu){EVIDENCE.put("cell_chest_native_configuration",true);capture("cell-chest-native-configuration");}
                }
                case 108 -> {mc.player.closeContainer();cellChestUse(mc);advance();}
                case 109 -> {
                    if(mc.screen instanceof thaumicenergistics.client.EssentiaTerminalScreen&&mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.entries().size()==1&&terminalAmount(menu,Aspects.WATER)==64){
                        EVIDENCE.put("cell_terminal_local_real_packets",true);capture("cell-terminal-local");
                    }
                }
                case 110 -> {terminalClick(Aspects.WATER,0);advance();}
                case 111 -> {click(11,ClickType.PICKUP);click(0,ClickType.PICKUP);advance();}
                case 112 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().getCount()==4&&terminalAmount(menu,Aspects.WATER)==32){
                        EVIDENCE.put("cell_terminal_work_transfer",true);capture("cell-terminal-filled");
                    }
                }
                case 113 -> {
                    click(1,ClickType.QUICK_MOVE);var screen=(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.screen;
                    mc.screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+17,0);mc.screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+37,0);advance();
                }
                case 114 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.sort()==1&&menu.view()==appeng.api.config.ViewItems.STORED){
                        server(player->{
                            var current=(thaumicenergistics.menu.EssentiaTerminalMenu)player.containerMenu;current.action(player,current.containerId,0,"");
                            player.getInventory().clearContent();
                            var empty=nativeItem("empty_phial");empty.setCount(2);current.getSlot(0).set(empty);
                            current.getSlot(1).set(thaumcraft.api.aspects.EssentiaItemApi.configureLabel(nativeItem("jar_label"),Aspects.WATER).orElseThrow());current.broadcastChanges();
                        });advance();
                    }
                }
                case 115 -> {
                    if(mc.player.containerMenu.getSlot(0).getItem().getCount()==2&&mc.player.containerMenu.getSlot(1).getItem().getCount()==1){mc.player.closeContainer();advance();}
                }
                case 116 -> {
                    server(player->{
                        int empty=0,labels=0;
                        var stacks=new ArrayList<ItemStack>();for(int slot=0;slot<36;slot++)stacks.add(player.getInventory().getItem(slot));
                        player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(CELL_CHEST).inflate(4)).forEach(entity->stacks.add(entity.getItem()));
                        for(var stack:stacks){if(stack.is(nativeItem("empty_phial").getItem()))empty+=stack.getCount();if(stack.is(nativeItem("jar_label").getItem()))labels+=stack.getCount();}
                        if(empty!=2||labels!=1)throw new IllegalStateException("Cell close lost or duplicated private work slots: "+empty+"/"+labels);
                        var chest=(appeng.blockentity.storage.MEChestBlockEntity)player.level().getBlockEntity(CELL_CHEST);
                        var remote=player.level().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,CELL_CHEST.east(2),Direction.UP).getStackInSlot(0);
                        if(new EssentiaCell(chest.getCell(),null).ledger().total()!=32||new EssentiaCell(remote,null).ledger().total()!=1000)throw new IllegalStateException("Cell terminal used remote network contents");
                    });EVIDENCE.put("cell_terminal_close_conserves_work_items",true);advance();
                }
                case 117 -> {if(mc.screen==null){cellChestUse(mc);advance();}}
                case 118 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.sort()==1&&menu.view()==appeng.api.config.ViewItems.STORED&&menu.selected()==null&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty()&&terminalAmount(menu,Aspects.WATER)==32){
                        EVIDENCE.put("cell_terminal_modes_reopen",true);capture("cell-terminal-reopened");
                    }
                }
                case 119 -> {server(player->((appeng.blockentity.storage.MEChestBlockEntity)player.level().getBlockEntity(CELL_CHEST)).setCell(new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K4).get())));advance();}
                case 120 -> {if(mc.screen==null&&mc.player.containerMenu==mc.player.inventoryMenu){EVIDENCE.put("cell_terminal_replacement_closes_menu",true);advance();}}
                case 121 -> {
                    server(player->{
                        var level=player.serverLevel();var cable=WIRELESS_AP.below();
                        for(var pos:List.of(WIRELESS_AP,cable,cable.west(),cable.east()))level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(WIRELESS_AP).inflate(4)).forEach(net.minecraft.world.entity.Entity::discard);
                        level.setBlockAndUpdate(WIRELESS_AP,block("ae2:wireless_access_point"));appeng.api.orientation.BlockOrientation.UP_NORTH.setOn(level,WIRELESS_AP);
                        level.setBlockAndUpdate(cable.west(),block("ae2:creative_energy_cell"));level.setBlockAndUpdate(cable.east(),block("ae2:drive"));
                        PartHelper.setPart(level,cable,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
                        var cell=new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());new EssentiaCell(cell,null).insert(EssentiaKey.of(Aspects.WATER),160,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
                        level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,cable.east(),Direction.UP).insertItem(0,cell,false);
                        player.getInventory().clearContent();player.getInventory().selected=0;player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
                        var terminal=new ItemStack(ThaumicEnergistics.WIRELESS_TERMINAL.get());ThaumicEnergistics.WIRELESS_TERMINAL.get().injectAEPower(terminal,10000,appeng.api.config.Actionable.MODULATE);player.setItemInHand(InteractionHand.MAIN_HAND,terminal);
                        var empty=nativeItem("empty_phial");empty.setCount(4);player.getInventory().setItem(9,empty);player.inventoryMenu.broadcastChanges();player.teleportTo(level,36.5,105.6,18.9,180,45);
                    });advance();
                }
                case 122 -> {
                    if(age>=40&&mc.player.getMainHandItem().is(ThaumicEnergistics.WIRELESS_TERMINAL.get())&&mc.level.getBlockEntity(WIRELESS_AP) instanceof appeng.blockentity.networking.WirelessAccessPointBlockEntity){
                        server(player->{var ap=(appeng.blockentity.networking.WirelessAccessPointBlockEntity)player.level().getBlockEntity(WIRELESS_AP);if(!ap.isActive())throw new IllegalStateException("Wireless fixture AP inactive");ap.getGrid().getCraftingService().addGlobalCraftingProvider(TerminalCraftingFixture.provider(player.level()));});
                        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(WIRELESS_AP).add(0,.501,0),Direction.UP,WIRELESS_AP,false));advance();
                    }
                }
                case 123 -> {
                    if(mc.player.containerMenu instanceof appeng.menu.implementations.WirelessAccessPointMenu apMenu){
                        int heldSlot=apMenu.slots.stream().filter(slot->slot.container==mc.player.getInventory()&&slot.getContainerSlot()==0).findFirst().orElseThrow().index;
                        if(apMenu.getCarried().isEmpty()&&!apMenu.getSlot(heldSlot).getItem().isEmpty()&&ThaumicEnergistics.WIRELESS_TERMINAL.get().getLinkedPosition(apMenu.getSlot(heldSlot).getItem())==null){click(heldSlot,ClickType.PICKUP);click(1,ClickType.PICKUP);}
                        else if(apMenu.getCarried().isEmpty()&&!apMenu.getSlot(2).getItem().isEmpty()&&ThaumicEnergistics.WIRELESS_TERMINAL.get().getLinkedPosition(apMenu.getSlot(2).getItem())!=null){click(2,ClickType.PICKUP);click(heldSlot,ClickType.PICKUP);}
                        else if(apMenu.getCarried().isEmpty()&&ThaumicEnergistics.WIRELESS_TERMINAL.get().getLinkedPosition(mc.player.getMainHandItem())!=null){
                            EVIDENCE.put("wireless_native_ap_binding_packet",true);mc.player.closeContainer();mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);advance();
                        }
                    }
                }
                case 124 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&terminalAmount(menu,Aspects.WATER)==160&&menu.entries().size()==2){EVIDENCE.put("wireless_initial_connection_real_packet",true);capture("wireless-terminal-main");}
                }
                case 125 -> {click(2,ClickType.PICKUP);mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,11,0,ClickType.SWAP,mc.player);advance();}
                case 126 -> {
                    if(mc.player.containerMenu.getSlot(2).getItem().is(ThaumicEnergistics.WIRELESS_TERMINAL.get())&&mc.player.containerMenu.getCarried().isEmpty()){
                        EVIDENCE.put("wireless_bound_slot_real_packets",true);terminalClick(Aspects.WATER,0);advance();
                    }
                }
                case 127 -> {click(11,ClickType.PICKUP);click(0,ClickType.PICKUP);advance();}
                case 128 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().getCount()==4&&terminalAmount(menu,Aspects.WATER)==128&&ThaumicEnergistics.WIRELESS_TERMINAL.get().getAECurrentPower(mc.player.getMainHandItem())<10000){EVIDENCE.put("wireless_work_and_battery_real_packets",true);capture("wireless-terminal-filled");}
                }
                case 129 -> {
                    click(1,ClickType.QUICK_MOVE);var screen=(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.screen;
                    mc.screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+17,0);mc.screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+37,0);advance();
                }
                case 130 -> {if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.sort()==1&&menu.view()==appeng.api.config.ViewItems.STORED){mc.player.closeContainer();advance();}}
                case 131 -> {if(mc.screen==null){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);advance();}}
                case 132 -> {
                    if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.sort()==1&&menu.view()==appeng.api.config.ViewItems.STORED&&menu.selected()==null&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty()&&terminalAmount(menu,Aspects.WATER)==128){EVIDENCE.put("wireless_modes_reopen",true);capture("wireless-terminal-reopened");}
                }
                case 133 -> {var screen=(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.screen;mc.screen.mouseClicked(screen.getGuiLeft()-10,screen.getGuiTop()+37,0);advance();}
                case 134 -> {if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu&&menu.view()==appeng.api.config.ViewItems.CRAFTABLE){terminalClick(Aspects.FIRE,0);advance();}}
                case 135 -> {if(mc.player.containerMenu instanceof appeng.menu.me.crafting.CraftAmountMenu menu&&menu.getWhatToCraft()!=null&&menu.getWhatToCraft().what().equals(EssentiaKey.of(Aspects.FIRE)))capture("wireless-native-craft-amount");}
                case 136 -> {((appeng.menu.me.crafting.CraftAmountMenu)mc.player.containerMenu).confirm(0,false,false);advance();}
                case 137 -> {if(mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu){EVIDENCE.put("wireless_native_craft_return",true);server(player->player.teleportTo(player.serverLevel(),136.5,105.6,18.9,180,45));advance();}}
                case 138 -> {if(mc.screen==null&&mc.player.containerMenu==mc.player.inventoryMenu){EVIDENCE.put("wireless_range_closes_menu",true);advance();}}
                case 139 -> {
                    server(player->{
                        if(!ResearchApi.knows(player,"thaumicenergistics:teessterm"))ResearchApi.grant(player,"thaumicenergistics:teessterm");
                        player.setItemInHand(InteractionHand.MAIN_HAND,nativeItem("thaumonomicon"));player.inventoryMenu.broadcastChanges();player.getMainHandItem().use(player.level(),player,InteractionHand.MAIN_HAND);
                    });advance();
                }
                case 140 -> {
                    if(mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("ThaumonomiconScreen")&&ResearchApi.clientKnowledge().completedResearch().contains("thaumicenergistics:teessterm")){
                        var definition=ResearchApi.definitions().get("thaumicenergistics:teessterm");
                        if(definition==null||definition.pageOrder().size()!=9||!definition.secondary()||!net.minecraft.client.resources.language.I18n.exists("tc.research_name."+definition.key()))throw new IllegalStateException("Monitoring research client sync/translation differs from release");
                        var open=mc.screen.getClass().getDeclaredMethod("openResearch",String.class);open.setAccessible(true);open.invoke(mc.screen,definition.key());advance();
                    }
                }
                case 141 -> capture("monitoring-research-text");
                case 142 -> {
                    var pages=mc.screen.getClass().getDeclaredMethod("pages");pages.setAccessible(true);var resolved=(List<?>)pages.invoke(mc.screen);
                    for(int index=2;index<=3;index++){
                        var recipe=resolved.get(index).getClass().getDeclaredMethod("recipe");recipe.setAccessible(true);
                        String expected="thaumicenergistics:"+(index==2?"essentia_terminal":"wireless_essentia_terminal");
                        if(!recipe.invoke(resolved.get(index)).equals(expected))throw new IllegalStateException("Monitoring recipe page mismatch "+index);
                    }
                    var spread=mc.screen.getClass().getDeclaredField("spread");spread.setAccessible(true);spread.setInt(mc.screen,1);EVIDENCE.put("monitoring_research_pages_real_sync",true);advance();
                }
                case 143 -> capture("monitoring-research-recipes");
                case 144 -> finish(true,null);
                default -> throw new IllegalStateException("Unknown smoke step " + step);
            }
            if (age > 600) throw new IllegalStateException("Client smoke step timeout " + step + "; screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName())
                    + "; held="+mc.player.getMainHandItem()+"; conversion="+(conversion(mc)==null?"missing":conversion(mc).getAmount()+"/locked="+conversion(mc).isLocked())
                    + (mc.player.containerMenu instanceof thaumicenergistics.menu.EssentiaTerminalMenu menu?"; terminal input="+menu.getSlot(0).getItem()+" output="+menu.getSlot(1).getItem()+" water="+terminalAmount(menu,Aspects.WATER):""));
        } catch (Throwable failure) { finish(false,failure); }
    }
    private static EssentiaLevelEmitterPart emitter(Minecraft mc) {
        var host=PartHelper.getPartHost(mc.level,CABLE);return host!=null&&host.getPart(Direction.UP) instanceof EssentiaLevelEmitterPart part?part:null;
    }
    private static void cellChestUse(Minecraft mc){
        var chest=(appeng.blockentity.storage.MEChestBlockEntity)mc.level.getBlockEntity(CELL_CHEST);var face=chest.getTop();
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(CELL_CHEST).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.501)),face,CELL_CHEST,false));
    }
    private static thaumicenergistics.parts.EssentiaConversionMonitorPart conversion(Minecraft mc){
        var host=PartHelper.getPartHost(mc.level,CONVERSION);
        return host!=null&&host.getPart(Direction.SOUTH) instanceof thaumicenergistics.parts.EssentiaConversionMonitorPart part?part:null;
    }
    private static long terminalAmount(thaumicenergistics.menu.EssentiaTerminalMenu menu,thaumcraft.api.aspects.Aspect aspect){
        return menu.entries().stream().filter(entry->entry.key().equals(EssentiaKey.of(aspect))).mapToLong(thaumicenergistics.storage.TerminalEntry::amount).findFirst().orElse(0);
    }
    private static void terminalClick(thaumcraft.api.aspects.Aspect aspect,int button){
        var mc=Minecraft.getInstance();var menu=(thaumicenergistics.menu.EssentiaTerminalMenu)mc.player.containerMenu;var screen=(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.screen;
        var rows=thaumicenergistics.storage.TerminalEntries.visible(menu.entries(),menu.sort(),menu.view(),editBox().getValue());
        int index=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).key().equals(EssentiaKey.of(aspect)))index=i;
        if(index<0||index>=36)throw new IllegalStateException("Terminal fixture aspect not visible: "+aspect);
        mc.screen.mouseClicked(screen.getGuiLeft()+7+index%9*18+9,screen.getGuiTop()+17+index/9*18+9,button);
    }
    private static void conversionUse(Minecraft mc){
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(CONVERSION).add(0,0,.501),Direction.SOUTH,CONVERSION,false));
    }
    private static ItemStack nativeItem(String path){return BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:"+path)).getDefaultInstance();}
    private static ItemStack phials(thaumcraft.api.aspects.Aspect aspect,int count){
        var result=thaumcraft.api.aspects.EssentiaItemApi.fill(nativeItem("empty_phial"),aspect,8).orElseThrow().resultStack();result.setCount(count);return result;
    }
    private static int portableUnits(net.minecraft.world.entity.player.Player player,thaumcraft.api.aspects.Aspect aspect){
        int total=0;for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);var info=thaumcraft.api.aspects.EssentiaItemApi.inspect(stack);
            if(info.isPresent()&&info.get().aspect()==aspect)total+=info.get().amount()*stack.getCount();
        }return total;
    }
    private static ItemStack ae(String path) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:"+path)).getDefaultInstance(); }
    private static void fixture(ServerPlayer player) {
        var level = player.serverLevel();
        var root = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!root.getFileName().toString().equals(SAVE) || !Files.isRegularFile(root.resolve(".thaumicenergistics-smoke-owned")))
            throw new IllegalStateException("Refusing to mutate an unmarked client save: " + root);
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.SMOOTH_STONE.defaultBlockState());
        level.setDayTime(6000); level.setWeatherParameters(20000,0,false,false);
        var drive = CABLE.north();
        level.setBlockAndUpdate(CABLE,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(drive,block("ae2:drive")); level.setBlockAndUpdate(drive.west(),block("ae2:creative_energy_cell"));
        for (var pos : List.of(CABLE.south(),CABLE.east(),CABLE.west())) {
            level.setBlockAndUpdate(pos,block("thaumcraft:warded_jar"));
            level.getCapability(EssentiaCapabilities.CONTAINER,pos,null).setAspects(new AspectList().add(Aspects.WATER,32));
        }
        PartHelper.setPart(level,CABLE,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var storage = PartHelper.setPart(level,CABLE,Direction.SOUTH,null,ThaumicEnergistics.STORAGE_BUS.get());
        PartHelper.setPart(level,CABLE,Direction.EAST,null,ThaumicEnergistics.IMPORT_BUS.get());
        var exporting = PartHelper.setPart(level,CABLE,Direction.WEST,null,ThaumicEnergistics.EXPORT_BUS.get()); exporting.filter(4,EssentiaKey.of(Aspects.WATER));
        level.setBlockAndUpdate(BENCH,ThaumicEnergistics.CELL_WORKBENCH.get().defaultBlockState());
        var cell = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        var contents = new EssentiaCell(cell,null); contents.insert(EssentiaKey.of(Aspects.WATER),20,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
        contents.insert(EssentiaKey.of(Aspects.EARTH),5,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
        level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,drive,Direction.UP).insertItem(0,cell.copy(),false);
        player.getInventory().clearContent(); player.setGameMode(GameType.CREATIVE);
        player.getInventory().setItem(0,ae("network_tool")); player.getInventory().selected = 8;
        player.getInventory().setItem(9,AspectApi.wispEssence(Aspects.WATER));
        var inverter = ae("inverter_card"); inverter.setCount(2); player.getInventory().setItem(10,inverter);
        var capacity = ae("capacity_card"); capacity.setCount(2); player.getInventory().setItem(11,capacity);
        player.getInventory().setItem(12,cell);
        player.getInventory().setItem(13,new ItemStack(ThaumicEnergistics.DIFFUSION_CORE.get()));
        player.getInventory().setItem(14,new ItemStack(ThaumicEnergistics.COALESCENCE_CORE.get()));
        player.teleportTo(level,4.5,100,6.5,160,25);
        storage.openMenu(player,false);
        Minecraft.getInstance().execute(ClientMenuSmoke::advance);
    }
    private static void visFixture(ServerPlayer player) {
        var level=player.serverLevel();
        for(int x=0;x<9;x++)for(int z=10;z<18;z++)level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.SMOOTH_STONE.defaultBlockState());
        for(var pos:List.of(VIS_INPUT,VIS_OUTPUT,VIS_INPUT.south()))level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(VIS_INPUT.east(),block("ae2:creative_energy_cell"));
        PartHelper.setPart(level,VIS_INPUT,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        PartHelper.setPart(level,VIS_INPUT.south(),null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        PartHelper.setPart(level,VIS_OUTPUT,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var input=PartHelper.setPart(level,VIS_INPUT,Direction.WEST,null,ThaumicEnergistics.VIS_INTERFACE.get());
        var output=PartHelper.setPart(level,VIS_OUTPUT,Direction.SOUTH,null,ThaumicEnergistics.VIS_INTERFACE.get());
        level.setBlockAndUpdate(VIS_INPUT.west(),block("thaumcraft:vis_relay").setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.WEST));
        level.setBlockAndUpdate(VIS_INPUT.west(2),block("thaumcraft:energized_aura_node"));
        level.setBlockAndUpdate(VIS_OUTPUT.south(),block("thaumcraft:vis_relay").setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.SOUTH));
        player.teleportTo(level,4.7,100.1,15.4,112,43);
    }
    private static net.minecraft.world.level.block.state.BlockState block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState(); }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        if (server == null) throw new IllegalStateException("Smoke requires the isolated integrated server");
        server.execute(() -> { try { var player = server.getPlayerList().getPlayer(id); if (player == null) throw new IllegalStateException("Local player missing"); action.accept(player); } catch (Throwable error) { SERVER_ERROR.set(error); } });
    }
    private static EditBox editBox() {
        return Minecraft.getInstance().screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }
    private static void click(int slot, ClickType type) { var mc = Minecraft.getInstance(); mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,type,mc.player); }
    private static void button(int id) { var mc = Minecraft.getInstance(); mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,id); }
    private static void advance() { step++; age = 0; System.out.println("THE_CLIENT_SMOKE_STEP " + step); }
    private static void capture(String name) {
        if (requestedCapture == null && awaitingCapture == null) requestedCapture = "the-"+RUN+"-"+name+".png";
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("thaumicenergistics.clientSmoke") || requestedCapture == null || finished) return;
        var mc = Minecraft.getInstance(); String name = requestedCapture; requestedCapture = null;
        awaitingCapture = mc.gameDirectory.toPath().resolve("screenshots").resolve(name);
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message -> System.out.println("THE_CLIENT_SMOKE_SCREENSHOT " + message.getString()));
    }
    private static void finish(boolean passed, Throwable failure) {
        finished = true; var mc = Minecraft.getInstance();
        EVIDENCE.put("automation_passed",passed); EVIDENCE.put("visual_review_pending",true); EVIDENCE.put("step",step); EVIDENCE.put("screenshots",CAPTURES);
        if (failure != null) { EVIDENCE.put("failure",failure.toString()); failure.printStackTrace(); }
        try {
            var output = mc.gameDirectory.toPath().resolve("thaumicenergistics-smoke/result.json"); Files.createDirectories(output.getParent());
            Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(EVIDENCE));
            System.out.println("THE_CLIENT_SMOKE_" + (passed ? "PASS" : "FAIL") + " " + output.toAbsolutePath());
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
