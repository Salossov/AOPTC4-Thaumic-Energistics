package thaumicenergistics.verification;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.*;
import thaumicenergistics.ThaumicEnergistics;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class EssentiaItemNativeTest {
    private static ItemStack item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:" + id)).getDefaultInstance();
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void nativePortableEssentiaCopiesAndQuantization(GameTestHelper h) {
        VerificationReports.install();
        var empty = item("empty_phial");empty.setCount(3);
        empty.set(DataComponents.CUSTOM_NAME, Component.literal("native preserved name"));
        var custom = new CompoundTag();custom.putString("Unrelated", "retain me");
        empty.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        var before = empty.copy();
        var info = EssentiaItemApi.inspect(empty).orElseThrow();
        h.assertTrue(info.capacity()==8 && !info.partial() && info.amount()==0 && info.aspect()==null, "native empty phial snapshot");
        for(int n=0;n<8;n++) h.assertTrue(EssentiaItemApi.fill(empty, Aspects.WATER,n).isEmpty(), "phial rejects partial fill " + n);
        var filled = EssentiaItemApi.fill(empty,Aspects.WATER,80).orElseThrow();
        h.assertTrue(filled.actualUnits()==8 && filled.resultStack().is(item("essentia_phial").getItem()), "fill converts exactly one full native phial");
        h.assertTrue(ItemStack.matches(empty,before), "fill leaves input count and components untouched");
        var phial = filled.resultStack();
        h.assertTrue(phial.getCount()==1 && phial.getHoverName().getString().equals("native preserved name") && phial.get(DataComponents.CUSTOM_DATA).copyTag().getString("Unrelated").equals("retain me"), "native item conversion preserves unrelated components");
        phial.setCount(17);
        h.assertTrue(filled.resultStack().getCount()==1, "result accessor returns independent copies");
        for(int n=0;n<8;n++) h.assertTrue(EssentiaItemApi.drain(filled.resultStack(),Aspects.WATER,n).isEmpty(), "phial rejects partial drain " + n);
        h.assertTrue(EssentiaItemApi.drain(filled.resultStack(),Aspects.FIRE,8).isEmpty() && EssentiaItemApi.fill(filled.resultStack(),Aspects.WATER,8).isEmpty(), "wrong aspect drain and full fill rejected");
        var drained = EssentiaItemApi.drain(filled.resultStack(),Aspects.WATER,100).orElseThrow();
        h.assertTrue(drained.actualUnits()==8 && drained.resultStack().is(empty.getItem()) && EssentiaItemApi.inspect(drained.resultStack()).orElseThrow().amount()==0, "full drain returns native empty phial");
        h.assertTrue(!drained.resultStack().get(DataComponents.CUSTOM_DATA).copyTag().contains("Aspect") && drained.resultStack().get(DataComponents.CUSTOM_DATA).copyTag().getString("Unrelated").equals("retain me"), "drain removes only contained aspect data");
        h.assertTrue(!(phial.getItem() instanceof IEssentiaContainerItem), "native phial not opted into processing bonus");
        for(var unrelated:new ItemStack[]{new ItemStack(Items.DIAMOND),item("essentia_crystal"),AspectApi.wispEssence(Aspects.WATER),item("jar_label"),ItemStack.EMPTY}) {
            h.assertTrue(EssentiaItemApi.inspect(unrelated).isEmpty() && EssentiaItemApi.fill(unrelated,Aspects.WATER,64).isEmpty() && EssentiaItemApi.drain(unrelated,Aspects.WATER,64).isEmpty(), "non-container rejected: " + unrelated);
        }
        var label=item("jar_label");label.setCount(5);label.set(DataComponents.CUSTOM_NAME,Component.literal("five labels"));
        var labelBefore=label.copy();var configured=EssentiaItemApi.configureLabel(label,Aspects.WATER).orElseThrow();
        h.assertTrue(configured.getCount()==5 && ((IAspectItem)configured.getItem()).getAspects(configured).getAmount(Aspects.WATER)==1 && configured.getHoverName().getString().equals("five labels"), "label whole stack configured without losing unrelated data");
        h.assertTrue(ItemStack.matches(label,labelBefore) && EssentiaItemApi.configureLabel(empty,Aspects.WATER).isEmpty(), "label input immutable and other item rejected");
        for(String id:new String[]{"warded_jar","void_jar"}) {
            var jar=item(id);jar.setCount(2);jar.set(DataComponents.CUSTOM_NAME,Component.literal(id));
            var data=new CompoundTag();data.putString("AspectFilter",Aspects.WATER.getTag());data.putInt("Unrelated",42);
            jar.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(data));var jarBefore=jar.copy();
            h.assertTrue(EssentiaItemApi.fill(jar,Aspects.FIRE,8).isEmpty(), "empty labelled jar refuses other aspect");
            var partial=EssentiaItemApi.fill(jar,Aspects.WATER,13).orElseThrow();
            var snapshot=EssentiaItemApi.inspect(partial.resultStack()).orElseThrow();
            h.assertTrue(partial.actualUnits()==13 && snapshot.capacity()==64 && snapshot.partial() && snapshot.amount()==13 && snapshot.filter()==Aspects.WATER, "jar partial fill and filter " + id);
            h.assertTrue(ItemStack.matches(jar,jarBefore) && partial.resultStack().is(jar.getItem()) && partial.resultStack().get(DataComponents.BLOCK_ENTITY_DATA).copyTag().getInt("Unrelated")==42, "jar identity/data and input preserved " + id);
            var full=EssentiaItemApi.fill(partial.resultStack(),Aspects.WATER,Integer.MAX_VALUE).orElseThrow();
            h.assertTrue(full.actualUnits()==51 && EssentiaItemApi.fill(full.resultStack(),Aspects.WATER,1).isEmpty(), "jar capacity64; no portable void overflow");
            var less=EssentiaItemApi.drain(full.resultStack(),Aspects.WATER,7).orElseThrow();
            h.assertTrue(less.actualUnits()==7 && EssentiaItemApi.inspect(less.resultStack()).orElseThrow().amount()==57, "jar partial drain");
            var drainedJar=EssentiaItemApi.drain(less.resultStack(),Aspects.WATER,64).orElseThrow().resultStack();
            var emptyInfo=EssentiaItemApi.inspect(drainedJar).orElseThrow();
            h.assertTrue(emptyInfo.amount()==0 && emptyInfo.aspect()==null && emptyInfo.filter()==Aspects.WATER && drainedJar.is(jar.getItem()), "empty jar keeps label and native identity");

            // Place the actual API result through native BlockItem, rather than loading a synthetic BE.
            var support=h.absolutePos(new BlockPos(id.equals("void_jar")?5:2,1,2));
            h.getLevel().setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(support.above()));
            var placing=partial.resultStack();player.setItemInHand(InteractionHand.MAIN_HAND,placing);
            var context=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false)));
            h.assertTrue(((BlockItem)placing.getItem()).place(context).consumesAction(), "native jar placement succeeds");
            var container=h.getLevel().getCapability(EssentiaCapabilities.CONTAINER,support.above(),null);
            h.assertTrue(container!=null && container.containerContains(Aspects.WATER)==13 && container.getAspectFilter()==Aspects.WATER, "placed native jar retains API contents/filter " + id);
            h.getLevel().setBlockAndUpdate(support.above(),Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(support,Blocks.AIR.defaultBlockState());
        }
        var mixed=item("warded_jar");var mixedTag=new CompoundTag();var aspects=new CompoundTag();aspects.putInt("aqua",1);aspects.putInt("ignis",1);mixedTag.put("Essentia",aspects);mixed.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(mixedTag));
        h.assertTrue(EssentiaItemApi.inspect(mixed).isEmpty() && EssentiaItemApi.fill(mixed,Aspects.WATER,8).isEmpty(), "malformed mixed jar not silently rewritten");
        var bad=item("essentia_phial");h.assertTrue(EssentiaItemApi.inspect(bad).isEmpty(), "unconfigured filled phial rejected");
        System.out.println("THE_NATIVE_ESSENTIA_ITEMS_V3_PASS");h.succeed();
    }
}
