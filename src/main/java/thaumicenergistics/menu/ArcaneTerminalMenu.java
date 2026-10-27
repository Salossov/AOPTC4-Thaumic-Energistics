package thaumicenergistics.menu;

import appeng.api.config.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.crafting.*;
import thaumcraft.api.research.ResearchApi;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.crafting.*;
import thaumicenergistics.network.*;
import thaumicenergistics.parts.ArcaneTerminalPart;

/** Shared part inventory, server-resolved recipes, and the original item-terminal actions. */
public final class ArcaneTerminalMenu extends AbstractContainerMenu {
    public static final int FIRST_GRID=36,LAST_GRID=44,RESULT=45,WAND=46,FIRST_VIEW=47,LAST_VIEW=51;
    private final Player owner;
    private final ArcaneTerminalPart part;
    private final ArcaneTerminalInventory inventory;
    private boolean refreshing,crafting;
    private int revision,pendingRevision=-1,pendingOffset;
    private List<ItemTerminalEntry> entries=List.of(),lastEntries;
    private final List<ItemTerminalEntry> pendingEntries=new ArrayList<>();
    private List<ArcaneCraftingLogic.Cost> costs=List.of(),lastCosts;
    private SortOrder sort=SortOrder.NAME,lastSort;
    private SortDir direction=SortDir.ASCENDING,lastDirection;
    private ViewItems view=ViewItems.ALL,lastView;
    public ArcaneTerminalMenu(int id,Inventory playerInventory,ArcaneTerminalPart part){
        super(ThaumicEnergistics.ARCANE_TERMINAL_MENU.get(),id);owner=playerInventory.player;this.part=part;
        inventory=part==null?new ArcaneTerminalInventory(slot->{}):part.inventory();
        for(int x=0;x<9;x++)addSlot(new Slot(playerInventory,x,8+x*18,220));
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(playerInventory,9+y*9+x,8+x*18,162+y*18));
        for(int y=0;y<3;y++)for(int x=0;x<3;x++)addSlot(new Slot(inventory,y*3+x,44+x*18,90+y*18));
        addSlot(new Slot(inventory,10,116,126){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player player){refresh();return stillValid(player)&&!getItem().isEmpty();}
            @Override public ItemStack remove(int amount){return amount>0?craft():ItemStack.EMPTY;}
            @Override public Optional<ItemStack> tryRemove(int amount,int maximum,Player player){
                refresh();if(amount<=0||getItem().isEmpty()||maximum<getItem().getCount()||!mayPickup(player))return Optional.empty();
                var result=craft();return result.isEmpty()?Optional.empty():Optional.of(result);
            }
            @Override public void onTake(Player player,ItemStack stack){crafted(stack);}
        });
        addSlot(new Slot(inventory,9,116,90){@Override public boolean mayPlace(ItemStack stack){return ArcaneTerminalInventory.validWand(stack);}@Override public int getMaxStackSize(){return 1;}});
        for(int i=11;i<=15;i++)addSlot(new Slot(inventory,i,206,8+(i-11)*18){@Override public boolean mayPlace(ItemStack stack){return ArcaneTerminalInventory.viewCell(stack);}});
        var types=new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for(int i=0;i<4;i++){final var equipment=types[i];
            addSlot(new Slot(inventory,16+i,8,81+i*18){@Override public boolean mayPlace(ItemStack stack){return stack.canEquip(equipment,owner);}@Override public int getMaxStackSize(){return 1;}});
            addSlot(new Slot(playerInventory,39-i,0,-1000){@Override public boolean mayPlace(ItemStack stack){return stack.canEquip(equipment,owner);}@Override public int getMaxStackSize(){return 1;}});
        }
        if(part!=null)part.listen(this);refresh();
    }
    public List<ItemTerminalEntry> entries(){return entries;}
    public List<ArcaneCraftingLogic.Cost> costs(){return costs;}
    public SortOrder sort(){return part==null?sort:part.sort();}
    public SortDir direction(){return part==null?direction:part.direction();}
    public ViewItems view(){return part==null?view:part.view();}
    public ArcaneTerminalInventory inventory(){return inventory;}
    public void inventoryChanged(){refresh();}
    @Override public boolean stillValid(Player player){return player==owner&&(part==null?player.level().isClientSide:part.valid(player));}
    private void refresh(){
        if(refreshing||crafting||!(owner instanceof ServerPlayer server)||!stillValid(owner))return;
        refreshing=true;try{var match=ArcaneCraftingLogic.match(inventory,server);costs=match==null?List.of():match.costs();inventory.result(match!=null&&match.enough()?match.output():ItemStack.EMPTY);}finally{refreshing=false;}
    }
    private ItemStack craft(){
        if(crafting||!(owner instanceof ServerPlayer server)||!stillValid(owner))return ItemStack.EMPTY;
        crafting=true;try{return ArcaneCraftingLogic.craft(inventory,server,this::replenish);}finally{crafting=false;refresh();}
    }
    private void crafted(ItemStack stack){
        stack.onCraftedBy(owner.level(),owner,stack.getCount());EventHooks.firePlayerCraftingEvent(owner,stack,inventory);refresh();
    }
    private ItemStack replenish(ItemStack wanted){
        var network=part==null?null:part.network();if(network==null)return ItemStack.EMPTY;
        // Original main-inventory priority, then hotbar, then ME exact/ore match.
        for(int offset=0;offset<36;offset++){int slot=offset<27?offset+9:offset-27;var stack=owner.getInventory().getItem(slot);
            if(ItemStack.isSameItemSameComponents(stack,wanted)&&!stack.isEmpty())return owner.getInventory().removeItem(slot,1);}
        var source=IActionSource.ofPlayer(owner,part);var exact=AEItemKey.of(wanted);
        if(network.extract(exact,1,Actionable.MODULATE,source)>0)return exact.toStack();
        // The grid's display cache may lag behind an insertion in this tick.
        // Legacy monitor.getStorageList() searches the current stored contents.
        for(var entry:network.getAvailableStacks())if(entry.getKey() instanceof AEItemKey key&&entry.getLongValue()>0&&equivalent(wanted,key.toStack()))
            if(network.extract(key,1,Actionable.MODULATE,source)>0)return key.toStack();
        return ItemStack.EMPTY;
    }
    private static boolean equivalent(ItemStack first,ItemStack second){
        if(first.isEmpty()||second.isEmpty())return false;
        if(first.getItem()==second.getItem()&&Objects.equals(first.get(DataComponents.DAMAGE),second.get(DataComponents.DAMAGE))
                &&Objects.equals(first.get(DataComponents.POTION_CONTENTS),second.get(DataComponents.POTION_CONTENTS)))return true;
        // OreDictionary IDs no longer exist. Match their material families,
        // excluding broad modern tags such as c:tools that were not ore IDs.
        var tags=first.getTags().filter(tag->{var id=tag.location();String name=id.getPath();return id.getNamespace().equals("c")&&List.of("ingots/","nuggets/","gems/","dusts/","rods/","plates/","gears/","dyes/","storage_blocks/").stream().anyMatch(name::startsWith)
                    ||id.getNamespace().equals("minecraft")&&(name.equals("planks")||name.equals("logs")||name.equals("wool"));}).toList();
        return tags.stream().anyMatch(second::is);
    }
    private boolean mergeNetwork(ItemStack stack){
        if(stack.isEmpty()||part==null||part.network()==null)return false;
        long amount=part.network().insert(AEItemKey.of(stack),stack.getCount(),Actionable.MODULATE,IActionSource.ofPlayer(owner,part));if(amount>0)stack.shrink((int)amount);return amount>0;
    }
    private boolean clearGrid(){
        boolean all=true;for(int i=0;i<9;i++){var stack=inventory.getItem(i);if(stack.isEmpty())continue;mergeNetwork(stack);if(stack.isEmpty())inventory.setItem(i,ItemStack.EMPTY);else{all=false;inventory.touch(i);}}refresh();return all;
    }
    @Override public void broadcastChanges(){refresh();sync();super.broadcastChanges();}
    private void sync(){
        if(part==null||!stillValid(owner)||!(owner instanceof ServerPlayer player))return;
        entries=part.entries();sort=part.sort();direction=part.direction();view=part.view();
        if(entries.equals(lastEntries)&&costs.equals(lastCosts)&&sort==lastSort&&direction==lastDirection&&view==lastView)return;
        int generation=++revision;
        for(int offset=0;offset<Math.max(1,entries.size());offset+=64){int end=Math.min(entries.size(),offset+64);
            PacketDistributor.sendToPlayer(player,new ArcaneTerminalStatePayload(containerId,generation,offset,end==entries.size(),sort,direction,view,costs,entries.subList(offset,end)));}
        lastEntries=entries;lastCosts=costs;lastSort=sort;lastDirection=direction;lastView=view;
    }
    public void receive(ArcaneTerminalStatePayload payload){
        if(part!=null||!owner.level().isClientSide||payload.menuId()!=containerId)return;
        if(payload.offset()==0){pendingEntries.clear();pendingRevision=payload.revision();pendingOffset=0;}
        if(payload.revision()!=pendingRevision||payload.offset()!=pendingOffset)return;
        pendingEntries.addAll(payload.entries());pendingOffset+=payload.entries().size();
        if(payload.last()){entries=List.copyOf(pendingEntries);costs=payload.costs();sort=payload.sort();direction=payload.direction();view=payload.view();pendingRevision=-1;}
    }
    public boolean action(Player player,ArcaneTerminalActionPayload request){
        if(part==null||player.containerMenu!=this||request.menuId()!=containerId||!stillValid(player))return false;
        switch(request.action()){
            case 0->lastEntries=null;
            case 1->{if(!part.isActive()||!extract(request.stack(),request.button(),request.shift()))return false;}
            case 2->{if(!part.isActive()||getCarried().isEmpty())return false;var held=getCarried();int wanted=request.button()==1||request.button()==-2?1:held.getCount();
                long accepted=part.network()==null?0:part.network().insert(AEItemKey.of(held),wanted,Actionable.MODULATE,IActionSource.ofPlayer(player,part));held.shrink((int)accepted);}
            case 3->{if(!part.isActive()||request.index()<0||request.index()>=36)return false;int start=request.index()<9?0:9,end=request.index()<9?9:36;
                for(int slot=start;slot<end;slot++){var item=getSlot(slot).getItem();if(mergeNetwork(item)){if(item.isEmpty())getSlot(slot).set(ItemStack.EMPTY);else getSlot(slot).setChanged();}}}
            case 4->{var values=SortOrder.values();int next=Math.floorMod(part.sort().ordinal()+(request.button()==1?-1:1),values.length);part.sorts(values[next],part.direction(),part.view());}
            case 5->part.sorts(part.sort(),part.direction()==SortDir.ASCENDING?SortDir.DESCENDING:SortDir.ASCENDING,part.view());
            case 6->{var values=ViewItems.values();int next=Math.floorMod(part.view().ordinal()+(request.button()==1?-1:1),values.length);part.sorts(part.sort(),part.direction(),values[next]);}
            case 7->clearGrid();
            case 8->part.swapArmor(player);
            case 9->{var key=AEItemKey.of(request.stack());var grid=part.getMainNode().getGrid();
                if(key==null||!part.isActive()||grid==null||!grid.getCraftingService().isCraftable(key)||!(player instanceof ServerPlayer server))return false;
                appeng.menu.me.crafting.CraftAmountMenu.open(server,appeng.menu.locator.MenuLocators.forPart(part),key,1);return true;}
            case 10->{if(!(player instanceof ServerPlayer server))return false;var id=ResourceLocation.tryParse(request.recipe());if(id==null||!fillRecipe(server,id))return false;}
            default->{return false;}
        }
        broadcastChanges();return true;
    }
    private boolean extract(ItemStack requested,int button,boolean shift){
        if(requested.isEmpty()||part.network()==null)return false;var key=AEItemKey.of(requested);long available=0;
        var grid=part.getMainNode().getGrid();if(grid!=null)available=grid.getStorageService().getCachedInventory().get(key);
        if(button==2){if(!owner.isCreative())return false;setCarried(key.toStack(key.getMaxStackSize()));return true;}
        int amount;
        if(button==-2||button==1&&shift)amount=shift?1:0;
        else if(button==0)amount=(int)Math.min(key.getMaxStackSize(),available);
        else if(button==1)amount=(int)Math.min((key.getMaxStackSize()+1)/2,(available+1)/2);
        else return false;
        int target=-1;
        if(button==0&&shift){target=mergeTarget(requested);if(target<0)return false;var item=owner.getInventory().getItem(target);amount=Math.min(amount,key.getMaxStackSize()-item.getCount());}
        else{var held=getCarried();if(!held.isEmpty()&&!key.matches(held))return false;amount=Math.min(amount,key.getMaxStackSize()-held.getCount());}
        if(amount<=0)return false;long taken=part.network().extract(key,amount,Actionable.MODULATE,IActionSource.ofPlayer(owner,part));if(taken<=0)return false;
        if(target>=0){var item=owner.getInventory().getItem(target);if(item.isEmpty())owner.getInventory().setItem(target,key.toStack((int)taken));else item.grow((int)taken);owner.getInventory().setChanged();}
        else if(getCarried().isEmpty())setCarried(key.toStack((int)taken));else getCarried().grow((int)taken);return true;
    }
    private int mergeTarget(ItemStack stack){
        for(boolean empty:new boolean[]{false,true})for(int i=0;i<36;i++){int index=i<27?i+9:i-27;var current=owner.getInventory().getItem(index);
            if(empty?current.isEmpty():ItemStack.isSameItemSameComponents(stack,current)&&current.getCount()<current.getMaxStackSize())return index;}
        return -1;
    }
    /** Viewer bridge: only recipe identity crosses the wire; fill keeps legacy one-per-slot intent. */
    public boolean fillRecipe(ServerPlayer player,ResourceLocation id){
        if(part==null||!part.isActive()||!stillValid(player)||player.containerMenu!=this)return false;
        var display=RecipeDisplays.recipes(player.level()).stream().filter(value->value.id().equals(id)).findFirst().orElse(null);
        RecipeHolder<?> holder;List<Ingredient> required=new ArrayList<>(Collections.nCopies(9,Ingredient.EMPTY));
        if(display!=null){
            if(display.kind()!=RecipeDisplay.Kind.ARCANE||display.recipeId()==null||display.inputs().size()!=9||display.research().stream().anyMatch(key->!ResearchApi.knows(player,key)))return false;
            holder=player.server.getRecipeManager().byKey(display.recipeId()).orElse(null);if(holder==null||!(holder.value() instanceof IArcaneRecipe))return false;required=display.inputs();
        }else{
            holder=player.server.getRecipeManager().byKey(id).orElse(null);if(holder==null||!(holder.value() instanceof CraftingRecipe recipe))return false;
            if(recipe instanceof ShapedRecipe shaped){
                if(shaped.getWidth()>3||shaped.getHeight()>3)return false;
                for(int y=0;y<shaped.getHeight();y++)for(int x=0;x<shaped.getWidth();x++)required.set(y*3+x,recipe.getIngredients().get(y*shaped.getWidth()+x));
            }else if(recipe instanceof ShapelessRecipe){
                if(recipe.getIngredients().size()>9)return false;
                for(int i=0;i<recipe.getIngredients().size();i++)required.set(i,recipe.getIngredients().get(i));
            }else return false;
        }
        if(!clearGrid())return false;
        var network=part.network();
        // clearGrid may just have returned the only matching items to ME.
        // The terminal's display cache can still be empty in this tick.
        var available=network==null?null:network.getAvailableStacks();
        for(int slot=0;slot<9;slot++){
            var ingredient=required.get(slot);if(ingredient.isEmpty())continue;
            ItemStack selected=ItemStack.EMPTY;
            for(int offset=0;offset<36&&selected.isEmpty();offset++){int index=offset<27?offset+9:offset-27;var value=player.getInventory().getItem(index);if(ingredient.test(value))selected=player.getInventory().removeItem(index,1);}
            if(selected.isEmpty()&&network!=null){for(var entry:available)
                if(entry.getKey() instanceof AEItemKey key&&entry.getLongValue()>0&&key.matches(ingredient)&&network.extract(key,1,Actionable.MODULATE,IActionSource.ofPlayer(player,part))>0){selected=key.toStack();break;}}
            if(!selected.isEmpty())inventory.setItem(slot,selected);
        }
        player.getInventory().setChanged();broadcastChanges();return true;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(stillValid(player))super.clicked(slot,button,type,player);}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return slot!=getSlot(RESULT)&&super.canTakeItemForPickAll(stack,slot);}
    @Override public void removed(Player player){super.removed(player);if(part!=null)part.unlisten(this);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player)||index<0||index>=slots.size()||!getSlot(index).hasItem())return ItemStack.EMPTY;
        if(index==RESULT){
            refresh();var initial=inventory.getItem(10).copy();int batch=initial.getCount();if(batch==0)return ItemStack.EMPTY;
            for(int produced=batch;produced<=64;produced+=batch){var preview=inventory.getItem(10);if(preview.isEmpty()||preview.getItem()!=initial.getItem()||!fitsInventory(preview))break;
                var result=craft();if(result.isEmpty())break;var eventStack=result.copy();moveItemStackTo(result,9,36,false);if(!result.isEmpty())moveItemStackTo(result,0,9,false);if(!result.isEmpty())player.drop(result,false);crafted(eventStack);}
            broadcastChanges();return ItemStack.EMPTY;
        }
        var slot=getSlot(index);var stack=slot.getItem();boolean moved=false;
        if(index>=FIRST_GRID&&index<=LAST_GRID)moved=mergeNetwork(stack)||moveItemStackTo(stack,0,9,false)||moveItemStackTo(stack,9,36,false);
        else if(index==WAND)moved=moveItemStackTo(stack,0,9,false)||moveItemStackTo(stack,9,36,false)||mergeNetwork(stack);
        else if(index>=FIRST_VIEW&&index<=LAST_VIEW)moved=moveItemStackTo(stack,0,9,false)||moveItemStackTo(stack,9,36,false);
        else if(index<36){
            if(ArcaneTerminalInventory.validWand(stack))moved=moveItemStackTo(stack,WAND,WAND+1,false);
            if(!moved&&ArcaneTerminalInventory.viewCell(stack))moved=moveItemStackTo(stack,FIRST_VIEW,LAST_VIEW+1,false);
            if(!moved)moved=mergeNetwork(stack)||moveItemStackTo(stack,FIRST_GRID,LAST_GRID+1,false)||moveItemStackTo(stack,index<9?9:0,index<9?36:9,false);
        }
        if(moved){if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();inventory.touch(-1);broadcastChanges();}
        return ItemStack.EMPTY;
    }
    private boolean fitsInventory(ItemStack stack){int room=0;for(int i=0;i<36;i++){var current=owner.getInventory().getItem(i);if(current.isEmpty())room+=stack.getMaxStackSize();else if(ItemStack.isSameItemSameComponents(current,stack))room+=stack.getMaxStackSize()-current.getCount();}return room>=stack.getCount();}
}
