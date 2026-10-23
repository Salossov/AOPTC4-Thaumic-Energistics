package thaumicenergistics.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.AspectApi;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumicenergistics.ThaumicEnergistics;

/** Fidelity fixtures transcribed from the release registrations, independent of generated JSON. */
@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class RecipeNativeTest {
    private static ItemStack stack(String id) {
        var key=ResourceLocation.parse(id);
        if(!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Missing native ingredient "+id);
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }
    private static CraftingInput grid(String... ids) {
        var items=new ArrayList<ItemStack>();
        for(var id:ids) items.add(id.isEmpty()?ItemStack.EMPTY:stack(id));
        while(items.size()<9)items.add(ItemStack.EMPTY);
        return CraftingInput.of(3,3,items);
    }
    @SuppressWarnings("unchecked")
    private static Recipe<CraftingInput> recipe(GameTestHelper h,String path) {
        return (Recipe<CraftingInput>)h.getLevel().getRecipeManager().byKey(ThaumicEnergistics.id(path)).orElseThrow().value();
    }
    private static void matches(GameTestHelper h,String path,CraftingInput grid,String output) {
        var recipe=recipe(h,path);
        h.assertTrue(recipe.matches(grid,h.getLevel()),"original ingredients match "+path);
        var result=recipe.assemble(grid,h.getLevel().registryAccess());
        h.assertTrue(result.is(stack(output).getItem())&&result.getCount()==1,"native result/count for "+path);
        var items=new ArrayList<ItemStack>(grid.items());
        for(int i=0;i<items.size();i++) if(!items.get(i).isEmpty()){items.set(i,ItemStack.EMPTY);break;}
        h.assertTrue(!recipe.matches(CraftingInput.of(grid.width(),grid.height(),items),h.getLevel()),"missing ingredient rejects "+path);
    }
    private static void arcane(GameTestHelper h,String path,String research,Map<String,Integer> vis) {
        var recipe=recipe(h,path);
        h.assertTrue(recipe instanceof IArcaneRecipe a && a.research().equals("thaumicenergistics:"+research) && a.vis().equals(vis),"whole-vis costs/research preserved for "+path);
        h.assertTrue(recipe.getType()!=RecipeType.CRAFTING,"ordinary crafting table cannot select arcane recipe "+path);
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeOriginalStorageAndVisRecipes(GameTestHelper h) {
        VerificationReports.install();
        String glass="thaumcraft:warded_glass", redstone="minecraft:redstone", metal="thaumcraft:thaumium_ingot";
        matches(h,"storage_casing",grid(glass,redstone,glass,redstone,"",redstone,metal,metal,metal),"thaumicenergistics:storage_casing");
        var tiers=List.of("1k","4k","16k","64k"); int[] order={1,2,4,8};
        for(int i=0;i<tiers.size();i++) {
            String tier=tiers.get(i), component="thaumicenergistics:storage_component_"+tier, cell="thaumicenergistics:essentia_cell_"+tier;
            matches(h,"essentia_cell_"+tier+"_shaped",grid(glass,redstone,glass,redstone,component,redstone,metal,metal,metal),cell);
            matches(h,"essentia_cell_"+tier+"_shapeless",grid("thaumicenergistics:storage_casing",component),cell);
            arcane(h,"storage_component_"+tier,"testorage",Map.of("ignis",3,"ordo",order[i]));
        }
        String wisp="thaumcraft:wisp_essence", quartz="ae2:certus_quartz_crystal", salis="thaumcraft:salis_mundus", qglass="ae2:quartz_glass";
        var one=grid(wisp,quartz,"",quartz,"ae2:logic_processor",quartz,"",quartz,wisp);
        matches(h,"storage_component_1k",one,"thaumicenergistics:storage_component_1k");
        var variants=new ArrayList<ItemStack>(one.items());
        for(int index:new int[]{1,3,5,7})variants.set(index,stack("ae2:charged_certus_quartz_crystal"));
        variants.set(0,AspectApi.wispEssence(Aspects.WATER)); variants.set(8,AspectApi.wispEssence(Aspects.FIRE));
        h.assertTrue(recipe(h,"storage_component_1k").matches(CraftingInput.of(3,3,variants),h.getLevel()),"original quartz group and arbitrary wisp essence aspects accepted");
        variants.set(1,stack("ae2:fluix_crystal"));
        h.assertTrue(!recipe(h,"storage_component_1k").matches(CraftingInput.of(3,3,variants),h.getLevel()),"quartz group does not broaden to fluix");
        matches(h,"storage_component_4k",grid(wisp,"ae2:calculation_processor",wisp,"thaumicenergistics:storage_component_1k",qglass,"thaumicenergistics:storage_component_1k",wisp,"thaumicenergistics:storage_component_1k",wisp),"thaumicenergistics:storage_component_4k");
        matches(h,"storage_component_16k",grid(salis,"ae2:engineering_processor",wisp,"thaumicenergistics:storage_component_4k",qglass,"thaumicenergistics:storage_component_4k",wisp,"thaumicenergistics:storage_component_4k",salis),"thaumicenergistics:storage_component_16k");
        matches(h,"storage_component_64k",grid(salis,"ae2:engineering_processor",salis,"thaumicenergistics:storage_component_16k",qglass,"thaumicenergistics:storage_component_16k",salis,"thaumicenergistics:storage_component_16k",salis),"thaumicenergistics:storage_component_64k");
        matches(h,"essentia_cell_workbench",grid("ae2:cell_workbench","thaumicenergistics:storage_component_1k"),"thaumicenergistics:essentia_cell_workbench");
        String drop="thaumcraft:quicksilver_drop", diffusion="thaumicenergistics:diffusion_core", coalescence="thaumicenergistics:coalescence_core";
        matches(h,"coalescence_core",grid("ae2:formation_core",drop,"thaumcraft:order_shard",drop,drop),coalescence);
        matches(h,"diffusion_core",grid("ae2:annihilation_core",drop,"thaumcraft:entropy_shard",drop,drop),diffusion);
        arcane(h,"coalescence_core","tecores",Map.of("aqua",2,"ordo",2)); arcane(h,"diffusion_core","tecores",Map.of("aqua",2,"perditio",2));
        String jar="thaumcraft:warded_jar", iron="minecraft:iron_ingot", tube="thaumcraft:filtered_essentia_tube";
        matches(h,"essentia_import_bus",grid(jar,diffusion,jar,iron,tube,iron),"thaumicenergistics:essentia_import_bus");
        matches(h,"essentia_export_bus",grid(jar,coalescence,jar,iron,tube,iron),"thaumicenergistics:essentia_export_bus");
        matches(h,"essentia_storage_bus",grid(diffusion,tube,coalescence,iron,glass,iron),"thaumicenergistics:essentia_storage_bus");
        h.assertTrue(recipe(h,"essentia_storage_bus").matches(grid("","","",coalescence,tube,diffusion,iron,glass,iron),h.getLevel()),"original mirrored storage bus also fits lower two rows");
        for(var bus:List.of("import","export"))arcane(h,"essentia_"+bus+"_bus","teio",Map.of("ignis",2,"terra",2,"aqua",1));
        arcane(h,"essentia_storage_bus","teio",Map.of("ignis",3,"terra",3,"aqua",1));
        matches(h,"vis_interface",grid("ae2:me_p2p_tunnel","thaumcraft:balanced_shard"),"thaumicenergistics:vis_interface");
        arcane(h,"vis_interface","tevisint",Map.of("aer",2,"terra",2,"ignis",2,"aqua",2,"ordo",2,"perditio",2));
        matches(h,"wireless_essentia_terminal",grid("ae2:wireless_receiver","thaumicenergistics:essentia_terminal","ae2:dense_energy_cell"),"thaumicenergistics:wireless_essentia_terminal");
        System.out.println("THE_NATIVE_ORIGINAL_RECIPES_PASS recipes=21"); h.succeed();
    }
}
