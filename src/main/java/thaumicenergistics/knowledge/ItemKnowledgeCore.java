package thaumicenergistics.knowledge;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Holds arcane crafting patterns.
 * Stack size 1, un-damageable, no player research tooltips (parity with 1.7.10 ItemKnowledgeCore).
 */
public class ItemKnowledgeCore extends Item {
    public ItemKnowledgeCore() {
        super(new Item.Properties().stacksTo(1).durability(0));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        // Original addInformation is empty: the core stores recipe patterns, not player research.
    }
}
