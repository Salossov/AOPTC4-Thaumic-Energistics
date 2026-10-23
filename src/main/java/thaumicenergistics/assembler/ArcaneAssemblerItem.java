package thaumicenergistics.assembler;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/** Source hint for crafted/wrenched machines carrying only their stored Vis. */
public final class ArcaneAssemblerItem extends BlockItem {
    public ArcaneAssemblerItem(ArcaneAssemblerBlock block) { super(block, new Properties()); }

    @Override public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private thaumicenergistics.client.AssemblerItemRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new thaumicenergistics.client.AssemblerItemRenderer();
                return renderer;
            }
        });
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains("stored_vis"))
            lines.add(Component.translatable("thaumicenergistics.tooltip.arcane.assembler.hasVis")
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
    }
}
