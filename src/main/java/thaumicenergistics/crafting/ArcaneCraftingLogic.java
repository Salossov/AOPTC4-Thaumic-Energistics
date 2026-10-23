package thaumicenergistics.crafting;

import java.util.*;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.research.ResearchApi;
import thaumcraft.api.wands.WandApi;

/** Recipe selection and debits use public contracts; no hidden workbench instance. */
public final class ArcaneCraftingLogic {
    private ArcaneCraftingLogic(){}
    public record Cost(Aspect aspect,int centivis,boolean enough){public float display(){return Math.round(centivis/10F)/10F;}}
    public record Match(RecipeHolder<? extends Recipe<CraftingInput>> recipe,CraftingInput.Positioned input,
                        Map<Aspect,Integer> wholeVis,List<Cost> costs,ItemStack output,boolean enough){
        public Match{wholeVis=Collections.unmodifiableMap(new LinkedHashMap<>(wholeVis));costs=List.copyOf(costs);output=output.copy();}
        @Override public ItemStack output(){return output.copy();}
    }
    public static CraftingInput.Positioned input(ArcaneTerminalInventory inventory){
        var stacks=new ArrayList<ItemStack>();for(int i=0;i<9;i++)stacks.add(inventory.getItem(i));return CraftingInput.ofPositioned(3,3,stacks);
    }
    @SuppressWarnings("unchecked")
    private static RecipeType<IArcaneRecipe> arcaneType(){return (RecipeType<IArcaneRecipe>)BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse("thaumcraft:arcane"));}
    public static Match match(ArcaneTerminalInventory inventory,ServerPlayer player){
        var positioned=input(inventory);var grid=positioned.input();var manager=player.server.getRecipeManager();
        var ordinary=manager.getRecipeFor(RecipeType.CRAFTING,grid,player.level());
        if(ordinary.isPresent())return new Match(ordinary.get(),positioned,Map.of(),List.of(),ordinary.get().value().assemble(grid,player.registryAccess()),true);
        var type=arcaneType();if(type==null)return null;
        for(var holder:manager.getAllRecipesFor(type)){
            var recipe=holder.value();
            if(!recipe.matches(grid,player.level())||!ResearchApi.knows(player,recipe.research())||!recipe.mayCraft(player,grid))continue;
            var vis=new LinkedHashMap<Aspect,Integer>();
            for(var entry:recipe.vis(grid).entrySet()){
                var aspect=Aspect.getAspect(entry.getKey());if(aspect==null||!aspect.isPrimal()||entry.getValue()<0)return null;
                if(entry.getValue()>0)vis.put(aspect,entry.getValue());
            }
            var wand=inventory.getItem(ArcaneTerminalInventory.WAND);boolean valid=ArcaneTerminalInventory.validWand(wand);
            var quoted=valid?WandApi.quoteCrafting(wand,player,vis):Map.<Aspect,Integer>of();
            var costs=new ArrayList<Cost>();boolean enough=true;
            for(var entry:vis.entrySet()){
                int amount=valid?quoted.get(entry.getKey()):Math.multiplyExact(entry.getValue(),100);
                boolean available=valid&&WandApi.stored(wand,entry.getKey())>=amount;
                enough&=available;costs.add(new Cost(entry.getKey(),amount,available));
            }
            return new Match(holder,positioned,vis,costs,recipe.assemble(grid,player.registryAccess()),enough);
        }
        return null;
    }
    public static ItemStack craft(ArcaneTerminalInventory inventory,ServerPlayer player,Function<ItemStack,ItemStack> replenish){
        if(!player.server.isSameThread())throw new IllegalStateException("Server thread required");
        var match=match(inventory,player);if(match==null||!match.enough()||match.output().isEmpty())return ItemStack.EMPTY;
        var remaining=match.recipe().value().getRemainingItems(match.input().input());
        var wand=inventory.getItem(ArcaneTerminalInventory.WAND);
        if(!match.wholeVis().isEmpty()&&!WandApi.consumeCrafting(player.level(),wand,player,match.wholeVis(),true))return ItemStack.EMPTY;
        for(int y=0;y<match.input().input().height();y++)for(int x=0;x<match.input().input().width();x++){
            int slot=(y+match.input().top())*3+x+match.input().left();var original=inventory.getItem(slot).copy();if(original.isEmpty())continue;
            var remainder=remaining.get(y*match.input().input().width()+x).copy();
            inventory.removeItem(slot,1);
            boolean putContainerInGrid=false;
            if(!remainder.isEmpty()){
                player.getInventory().add(remainder);
                if(!remainder.isEmpty()){
                    var current=inventory.getItem(slot);
                    if(current.isEmpty()){inventory.setItem(slot,remainder);putContainerInGrid=true;}
                    else if(ItemStack.isSameItemSameComponents(current,remainder)&&current.getCount()+remainder.getCount()<=current.getMaxStackSize()){
                        current.grow(remainder.getCount());inventory.touch(slot);putContainerInGrid=true;
                    }else{
                        // User-approved fix: the release overwrites the entire
                        // remaining ingredient stack when its container will not fit.
                        player.drop(remainder,false);
                    }
                }
            }
            if(original.getCount()==1&&!putContainerInGrid&&inventory.getItem(slot).isEmpty()){
                var replacement=replenish.apply(original);if(!replacement.isEmpty())inventory.setItem(slot,replacement);
            }
        }
        inventory.touch(ArcaneTerminalInventory.WAND);player.getInventory().setChanged();return match.output();
    }
}
