package thaumicenergistics.crafting;

import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import thaumicenergistics.ThaumicEnergistics;

/** Release FeatureGearbox chooses gearWood, falling back to a stick only if its tag is empty.
 * One recipe ID keeps its research page valid for either dependency/tag configuration.
 */
public record WoodGearIngredient() implements ICustomIngredient {
    private static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:gears/wooden"));
    private static boolean hasGears() { return BuiltInRegistries.ITEM.getTag(TAG).filter(tag -> tag.size() > 0).isPresent(); }
    @Override public boolean test(ItemStack stack) { return hasGears() ? stack.is(TAG) : stack.is(Items.STICK); }
    @Override public Stream<ItemStack> getItems() {
        return hasGears() ? BuiltInRegistries.ITEM.getTag(TAG).orElseThrow().stream().map(holder -> holder.value().getDefaultInstance())
                : Stream.of(new ItemStack(Items.STICK));
    }
    @Override public boolean isSimple() { return false; }
    @Override public IngredientType<?> getType() { return ThaumicEnergistics.WOOD_GEAR_INGREDIENT.get(); }
}
