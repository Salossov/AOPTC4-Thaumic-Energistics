package thaumicenergistics.crafting;

import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.*;
import thaumcraft.api.aspects.EssentiaItemApi;
import thaumicenergistics.ThaumicEnergistics;

/** Old blockJar metadata0 was a different item from filled/labelled jars. */
public record EmptyJarIngredient() implements ICustomIngredient {
    private static final ResourceLocation JAR=ResourceLocation.parse("thaumcraft:warded_jar");
    @Override public boolean test(ItemStack stack){return stack.is(BuiltInRegistries.ITEM.get(JAR))&&EssentiaItemApi.inspect(stack).filter(contents->contents.amount()==0&&contents.filter()==null).isPresent();}
    @Override public Stream<ItemStack> getItems(){return Stream.of(BuiltInRegistries.ITEM.get(JAR).getDefaultInstance());}
    @Override public boolean isSimple(){return false;}
    @Override public IngredientType<?> getType(){return ThaumicEnergistics.EMPTY_JAR_INGREDIENT.get();}
}
