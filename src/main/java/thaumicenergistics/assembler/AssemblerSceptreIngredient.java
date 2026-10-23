package thaumicenergistics.assembler;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.wands.WandApi;
import thaumcraft.api.wands.WandParts;

/** The release's infusion ingredient is a fully charged silverwood/thaumium sceptre.
 * Extra data, such as a custom name, is allowed just like TC4's crafting NBT subset check.
 */
public final class AssemblerSceptreIngredient implements ICustomIngredient {
    public static final AssemblerSceptreIngredient INSTANCE = new AssemblerSceptreIngredient();
    public static final MapCodec<AssemblerSceptreIngredient> CODEC = MapCodec.unit(INSTANCE);
    private static final WandParts PARTS = new WandParts("silverwood", "thaumium");

    @Override public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty() || WandApi.kind(stack) != WandApi.Kind.SCEPTRE
                || !PARTS.equals(WandApi.parts(stack))) return false;
        int capacity = WandApi.capacity(stack);
        if (capacity <= 0) return false;
        for (String tag : AssemblerVisBuffer.PRIMALS)
            if (WandApi.stored(stack, Aspect.getAspect(tag)) != capacity) return false;
        return true;
    }

    @Override public Stream<ItemStack> getItems() {
        ItemStack example = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("thaumcraft:casting_sceptre")));
        if (example.isEmpty()) return Stream.empty();
        WandApi.parts(example, PARTS);
        int capacity = WandApi.capacity(example);
        for (String tag : AssemblerVisBuffer.PRIMALS) WandApi.set(example, Aspect.getAspect(tag), capacity);
        return Stream.of(example);
    }

    @Override public boolean isSimple() { return false; }
    @Override public IngredientType<?> getType() { return AssemblerRegistration.SCEPTRE_INGREDIENT.get(); }
    private AssemblerSceptreIngredient() { }
}
