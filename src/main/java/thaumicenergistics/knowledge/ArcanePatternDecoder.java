package thaumicenergistics.knowledge;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Restores an individual recipe persisted by an AE2 crafting CPU. */
final class ArcanePatternDecoder implements IPatternDetailsDecoder {
    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        return KnowledgeCoreAccess.isKnowledgeCore(stack) && stack.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .contains(ArcaneCraftingPattern.DEFINITION_TAG, Tag.TAG_COMPOUND);
    }

    @Override
    public IPatternDetails decodePattern(AEItemKey definition, Level level) {
        ItemStack stack = definition.toStack();
        if (level == null || !isEncodedPattern(stack)) return null;
        try {
            var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            var pattern = new ArcaneCraftingPattern(stack,
                    tag.getCompound(ArcaneCraftingPattern.DEFINITION_TAG), level.registryAccess());
            return pattern.isPatternValid() ? pattern : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
