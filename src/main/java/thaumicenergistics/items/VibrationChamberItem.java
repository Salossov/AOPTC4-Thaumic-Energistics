package thaumicenergistics.items;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.aspects.*;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.ChamberContents;

public final class VibrationChamberItem extends BlockItem implements IAspectItem {
    public VibrationChamberItem(Block block){super(block,new Item.Properties());}
    public static boolean accepts(Aspect aspect){return aspect==Aspects.FIRE||aspect==Aspects.ENERGY;}
    public static ChamberContents contents(ItemStack stack){return stack.getOrDefault(ThaumicEnergistics.CHAMBER_CONTENTS,ChamberContents.EMPTY);}
    public static void contents(ItemStack stack,ChamberContents value){if(value.amount()==0)stack.remove(ThaumicEnergistics.CHAMBER_CONTENTS);else stack.set(ThaumicEnergistics.CHAMBER_CONTENTS,value);}
    @Override public AspectList getAspects(ItemStack stack){var value=contents(stack);return value.aspect()==null?new AspectList():new AspectList().add(value.aspect(),value.amount());}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);var stored=contents(stack);
        if(stored.amount()>0){
            if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient()&&net.minecraft.client.gui.screens.Screen.hasShiftDown())lines.add(Component.literal((stored.aspect()==null?stored.tag():stored.aspect().getName())+" x "+stored.amount()));
            else lines.add(Component.translatable("thaumicenergistics.tooltip.itemstack.details").withStyle(net.minecraft.ChatFormatting.WHITE,net.minecraft.ChatFormatting.ITALIC));
        }
    }
}
