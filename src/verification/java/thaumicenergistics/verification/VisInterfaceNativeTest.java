package thaumicenergistics.verification;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.visnet.TileVisNode;
import thaumcraft.api.visnet.VisNetHandler;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.VisInterfacePart;
import thaumicenergistics.vis.DigiVisSourceData;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class VisInterfaceNativeTest {
    private static net.minecraft.world.level.block.Block block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)); }
    private static ItemStack card() { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:memory_card"))); }
    private static void use(VisInterfacePart part, ItemStack card, Player player, boolean shift) {
        player.setShiftKeyDown(shift); part.onUseItemOn(card,player,InteractionHand.MAIN_HAND,Vec3.ZERO); player.setShiftKeyDown(false);
    }
    private static VisInterfacePart part(GameTestHelper h, BlockPos p) {
        PartHelper.setPart(h.getLevel(),p,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        return PartHelper.setPart(h.getLevel(),p,Direction.NORTH,null,ThaumicEnergistics.VIS_INTERFACE.get());
    }
    private static void clear(GameTestHelper h) {
        for (int x=0;x<9;x++) for (int y=1;y<6;y++) for (int z=0;z<9;z++) h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(x,y,z)),Blocks.AIR.defaultBlockState());
    }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void nativeVisInterfaceMemoryAndPower(GameTestHelper h) {
        VerificationReports.install(); var level=h.getLevel();
        var inputPos=h.absolutePos(new BlockPos(2,3,3)); var outputPos=inputPos.east(4);
        // Separate the two VisNet rooms while keeping one actual AE2 cable through the wall.
        for (int y=1;y<6;y++) for(int z=0;z<9;z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(4,y,z)),Blocks.OBSIDIAN.defaultBlockState());
        for(int x=2;x<=6;x++) PartHelper.setPart(level,h.absolutePos(new BlockPos(x,3,3)),null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var input=part(h,inputPos); var output=part(h,outputPos);
        var relay=block("thaumcraft:vis_relay").defaultBlockState().setValue(BlockStateProperties.FACING,Direction.NORTH);
        level.setBlockAndUpdate(inputPos.north(),relay); level.setBlockAndUpdate(outputPos.north(),relay);
        var outlet=outputPos.north().east(); level.setBlockAndUpdate(outlet,relay);
        level.setBlockAndUpdate(inputPos.north().west(),block("thaumcraft:energized_aura_node").defaultBlockState());
        var batteryPos=inputPos.south(); level.setBlockAndUpdate(batteryPos,block("ae2:energy_cell").defaultBlockState());
        ((appeng.api.networking.energy.IAEPowerStorage)level.getBlockEntity(batteryPos)).injectAEPower(1000,Actionable.MODULATE);
        var player=h.makeMockPlayer(GameType.SURVIVAL); player.setPos(inputPos.getX(),inputPos.getY(),inputPos.getZ());
        h.runAfterDelay(50,() -> {
            h.assertTrue(input.isActive() && output.isActive() && input.getGrid()==output.getGrid(),"two actual channel-using endpoints share a booted finite-power grid");
            h.assertTrue(input.getRelay()!=null,"public TileVisNode plus native facing state resolves the exact front relay");
            level.setBlockAndUpdate(inputPos.north(),relay.setValue(BlockStateProperties.FACING,Direction.SOUTH));
            h.assertTrue(input.getRelay()==null,"wrong relay orientation cannot drain"); level.setBlockAndUpdate(inputPos.north(),relay);
            var energy=input.getGrid().getEnergyService(); var multiplier=PowerMultiplier.CONFIG;
            energy.extractAEPower(1e9,Actionable.MODULATE,multiplier); energy.injectPower(3,Actionable.MODULATE);
            h.assertTrue(input.consumeVis(Aspects.AIR,1000)==0 && Math.abs(energy.extractAEPower(1e9,Actionable.SIMULATE,multiplier)-3)<1e-7,"three AE refuses entire request without spending vis or power");
            energy.injectPower(1,Actionable.MODULATE);
            h.assertTrue(input.consumeVis(Aspects.AIR,1000)==4 && energy.extractAEPower(1e9,Actionable.SIMULATE,multiplier)<1e-7,"four AE buys one successful request of four finite centivis, not four AE per unit");
            energy.injectPower(100,Actionable.MODULATE);
            double before=energy.extractAEPower(1e9,Actionable.SIMULATE,multiplier);
            h.assertTrue(input.consumeVis(Aspects.AIR,1000)==0 && input.consumeVis(Aspects.MAGIC,1)==0 && input.consumeVis(Aspects.AIR,0)==0
                    && energy.extractAEPower(1e9,Actionable.SIMULATE,multiplier)==before,"empty budget, compound aspect and empty request do not charge");
            var memory=card(); use(input,memory,player,true); use(output,memory,player,false);
            h.assertTrue(!input.isVisProvider() && output.isVisProvider(),"actual AE2 memory card saves input identity and configures output mode");
            h.assertTrue(DigiVisSourceData.of(input).equals(DigiVisSourceData.read(DigiVisSourceData.of(input).write())),"modern dimension/position/side/UID link round-trips");
            var saved=new CompoundTag(); output.writeToNBT(saved,level.registryAccess());
            var copy=new VisInterfacePart(ThaumicEnergistics.VIS_INTERFACE.get()); copy.readFromNBT(saved,level.registryAccess());
            h.assertTrue(copy.getUID()==output.getUID() && copy.isVisProvider(),"world NBT preserves endpoint identity and linked provider mode");
            h.runAfterDelay(1,() -> {
                output.tickingRequest(output.getGridNode(),30); VisNetHandler.invalidate(level); VisNetHandler.refresh(level);
                var downstream=(TileVisNode)level.getBlockEntity(outlet);
                h.assertTrue(downstream.getRootSource()!=null && downstream.getRootSource().getBlockPos().equals(outputPos.north()),"real downstream relay roots at the virtual output, across opaque rooms");
                double power=energy.extractAEPower(1e9,Actionable.SIMULATE,multiplier);
                h.assertTrue(downstream.consumeVis(Aspects.AIR,100)==4 && Math.abs(power-energy.extractAEPower(1e9,Actionable.SIMULATE,multiplier)-4)<1e-7,"actual VisNet relay receives finite remote centivis and charges exactly once");
                h.assertTrue(input.drainColor()==Aspects.AIR.getColor() && output.drainColor()==Aspects.AIR.getColor(),"both endpoints synchronize successful-drain aspect color");
                use(output,card(),player,false);
                h.assertTrue(!output.isVisProvider() && VisNetHandler.loadedNodes(level).stream().noneMatch(n->n.isSource()&&n.getBlockPos().equals(outputPos.north())),"blank card clears provider and closes virtual registration");
                use(input,memory,player,false); h.assertTrue(!input.isVisProvider(),"self link is rejected");
                use(output,memory,player,false); output.tickingRequest(output.getGridNode(),30);
                var oldIdentity=DigiVisSourceData.of(input); PartHelper.getPartHost(level,inputPos).removePart(input);
                var replacement=PartHelper.setPart(level,inputPos,Direction.NORTH,null,ThaumicEnergistics.VIS_INTERFACE.get());
                h.assertTrue(replacement.getUID()!=oldIdentity.uid() && oldIdentity.resolve(level.getServer(),output.getGrid())==null && output.consumeVis(Aspects.AIR,1)==0,"replacing source part cannot hijack persisted UID link");
                output.tickingRequest(output.getGridNode(),30);
                h.assertTrue(VisNetHandler.loadedNodes(level).stream().noneMatch(n->n.isSource()&&n.getBlockPos().equals(outputPos.north())),"lost link removes output source");
                clear(h); System.out.println("THE_NATIVE_VIS_INTERFACE_MEMORY_POWER_PASS"); h.succeed();
            });
        });
    }
    @GameTest(template = "empty", timeoutTicks = 140)
    public static void nativeVisInterfaceRecursiveRoute(GameTestHelper h) {
        VerificationReports.install(); var level=h.getLevel(); var a=h.absolutePos(new BlockPos(2,2,3)); var b=a.east(2);
        PartHelper.setPart(level,a.east(),null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var input=part(h,a); var output=part(h,b);
        var relay=block("thaumcraft:vis_relay").defaultBlockState().setValue(BlockStateProperties.FACING,Direction.NORTH);
        level.setBlockAndUpdate(a.north(),relay); level.setBlockAndUpdate(b.north(),relay);
        level.setBlockAndUpdate(a.south(),block("ae2:energy_cell").defaultBlockState());
        ((appeng.api.networking.energy.IAEPowerStorage)level.getBlockEntity(a.south())).injectAEPower(1000,Actionable.MODULATE);
        var player=h.makeMockPlayer(GameType.SURVIVAL); var memory=card();
        h.runAfterDelay(50,() -> {
            use(input,memory,player,true); use(output,memory,player,false); output.tickingRequest(output.getGridNode(),30);
            VisNetHandler.invalidate(level); VisNetHandler.refresh(level);
            var root=((TileVisNode)level.getBlockEntity(a.north())).getRootSource();
            h.assertTrue(root!=null && root.getBlockPos().equals(b.north()),"fixture creates actual cyclic VisNet to DigiVis path without a native generator");
            var energy=input.getGrid().getEnergyService(); double before=energy.extractAEPower(1e9,Actionable.SIMULATE,PowerMultiplier.CONFIG);
            for(int i=0;i<40;i++) h.assertTrue(output.consumeVis(Aspects.AIR,100)==0 && VisNetHandler.drainVis(level,a.north(2),Aspects.AIR,100)==0,"recursive virtual route terminates without creating centivis");
            h.assertTrue(energy.extractAEPower(1e9,Actionable.SIMULATE,PowerMultiplier.CONFIG)==before,"failed cyclic requests never charge four AE");
            PartHelper.getPartHost(level,b).removePart(output);
            h.assertTrue(VisNetHandler.loadedNodes(level).stream().noneMatch(n->n.isSource()&&n.getBlockPos().equals(b.north())),"part removal closes actual virtual source immediately");
            var replacement=PartHelper.setPart(level,b,Direction.NORTH,null,ThaumicEnergistics.VIS_INTERFACE.get());
            use(replacement,memory,player,false); replacement.tickingRequest(replacement.getGridNode(),30);
            h.assertTrue(replacement.isVisProvider(),"new output can bind the still-live source identity");
            clear(h); System.out.println("THE_NATIVE_VIS_INTERFACE_RECURSION_PASS"); h.succeed();
        });
    }
}
