package thaumicenergistics.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import java.util.Optional;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.aspects.EssentiaItemApi;

/** The original two-slot terminal work cycle; one inventory shared by all open menus. */
public final class TerminalInventory extends SimpleContainer {
    private final Runnable dirty;
    public TerminalInventory(Runnable dirty){super(2);this.dirty=dirty;}
    public static boolean accepts(ItemStack item){
        return thaumicenergistics.storage.PortableAspectItems.inspect(item).isPresent()||EssentiaItemApi.configureLabel(item,Aspects.AIR).isPresent();
    }
    @Override public boolean canPlaceItem(int slot,ItemStack item){return slot==0&&accepts(item);}
    @Override public void setChanged(){super.setChanged();if(dirty!=null)dirty.run();}
    public static Optional<ItemStack> quote(ItemStack item,Aspect selected,MEStorage powered,IActionSource source){
        if(selected!=null){var label=EssentiaItemApi.configureLabel(item,selected);if(label.isPresent())return label;}
        var contents=thaumicenergistics.storage.PortableAspectItems.inspect(item);if(contents.isEmpty())return Optional.empty();
        var transfer=contents.get().amount()>0?PortableEssentiaTransfers.quoteDeposit(item,null,powered,source)
                :selected==null?Optional.<EssentiaItemApi.TransferResult>empty():PortableEssentiaTransfers.quoteFill(item,selected,powered,source);
        return transfer.map(EssentiaItemApi.TransferResult::resultStack);
    }
    public record Conversion(ItemStack result,int work){}
    public static Optional<Conversion> convert(ItemStack item,Aspect selected,MEStorage powered,MEStorage raw,IActionSource source){
        if(selected!=null){var label=EssentiaItemApi.configureLabel(item,selected);if(label.isPresent())return Optional.of(new Conversion(label.get(),1));}
        var contents=thaumicenergistics.storage.PortableAspectItems.inspect(item);if(contents.isEmpty())return Optional.empty();
        var transfer=contents.get().amount()>0?PortableEssentiaTransfers.deposit(item,null,powered,raw,source)
                :selected==null?Optional.<EssentiaItemApi.TransferResult>empty():PortableEssentiaTransfers.fill(item,selected,powered,raw,source);
        return transfer.map(result->new Conversion(result.resultStack(),result.actualUnits()));
    }
    private boolean fits(ItemStack stack){
        var output=getItem(1);return output.isEmpty()||ItemStack.isSameItemSameComponents(output,stack)&&output.getCount()+stack.getCount()<=output.getMaxStackSize();
    }
    public int process(Aspect selected,MEStorage powered,MEStorage raw,IActionSource source){
        int work=0;
        while(work<64&&!getItem(0).isEmpty()){
            var input=getItem(0).copyWithCount(1);
            var quote=quote(input,selected,powered,source);if(quote.isEmpty()||!fits(quote.get()))break;
            boolean label=thaumicenergistics.storage.PortableAspectItems.inspect(input).isEmpty();
            if(label)work++; // Release counts label work in SIMULATE as well as MODULATE: 32/cycle.
            var transfer=convert(input,selected,powered,raw,source);if(transfer.isEmpty())break;
            var actual=transfer.get();
            if(!fits(actual.result())){
                var contents=thaumicenergistics.storage.PortableAspectItems.inspect(input).orElseThrow();
                var key=EssentiaKey.of(contents.amount()>0?contents.aspect():selected);
                long restored=contents.amount()>0?raw.extract(key,actual.work(),Actionable.MODULATE,source):raw.insert(key,actual.work(),Actionable.MODULATE,source);
                if(restored!=actual.work())throw new IllegalStateException("Terminal output changed; rollback failed");
                break;
            }
            removeItem(0,1);
            if(getItem(1).isEmpty())setItem(1,actual.result());else{getItem(1).grow(actual.result().getCount());setChanged();}
            work+=actual.work();
        }
        return work;
    }
}
