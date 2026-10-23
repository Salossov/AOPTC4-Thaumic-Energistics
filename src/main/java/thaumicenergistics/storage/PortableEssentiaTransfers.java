package thaumicenergistics.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.EssentiaItemApi;

/** Server-thread conversion steps; the caller replaces one item only after success. */
public final class PortableEssentiaTransfers {
    public static Optional<EssentiaItemApi.TransferResult> deposit(ItemStack item, Aspect required,
            MEStorage powered, MEStorage raw, IActionSource source) {
        var quote=quoteDeposit(item,required,powered,source);
        if(quote.isEmpty())return Optional.empty();
        var contents=thaumicenergistics.storage.PortableAspectItems.inspect(item).orElseThrow();
        var key=EssentiaKey.of(contents.aspect());
        int actual=(int)powered.insert(key,quote.get().actualUnits(),Actionable.MODULATE,source);
        var result=thaumicenergistics.storage.PortableAspectItems.drain(item,contents.aspect(),actual);
        if(result.isEmpty() && actual>0) {
            // Same-thread endpoints normally honour their simulation. Compensate if not.
            if(raw.extract(key,actual,Actionable.MODULATE,source)!=actual)
                throw new IllegalStateException("Portable deposit changed during transfer; rollback failed");
        }
        return result;
    }
    public static Optional<EssentiaItemApi.TransferResult> quoteDeposit(ItemStack item, Aspect required,
            MEStorage powered, IActionSource source) {
        var found=thaumicenergistics.storage.PortableAspectItems.inspect(item);
        if(found.isEmpty())return Optional.empty();
        var contents=found.get();
        if(contents.amount()==0 || required!=null && contents.aspect()!=required)return Optional.empty();
        var key=EssentiaKey.of(contents.aspect());
        int accepted=(int)powered.insert(key,contents.amount(),Actionable.SIMULATE,source);
        // Approved fix: an eight-unit phial cannot be consumed for a partial deposit.
        return thaumicenergistics.storage.PortableAspectItems.drain(item,contents.aspect(),accepted);
    }

    public static Optional<EssentiaItemApi.TransferResult> fill(ItemStack item, Aspect aspect,
            MEStorage powered, MEStorage raw, IActionSource source) {
        var quote=quoteFill(item,aspect,powered,source);
        if(quote.isEmpty())return Optional.empty();
        var key=EssentiaKey.of(aspect);
        int actual=(int)powered.extract(key,quote.get().actualUnits(),Actionable.MODULATE,source);
        var result=thaumicenergistics.storage.PortableAspectItems.fill(item,aspect,actual);
        if(result.isEmpty() && actual>0) {
            if(raw.insert(key,actual,Actionable.MODULATE,source)!=actual)
                throw new IllegalStateException("Portable fill changed during transfer; rollback failed");
        }
        return result;
    }
    public static Optional<EssentiaItemApi.TransferResult> quoteFill(ItemStack item, Aspect aspect,
            MEStorage powered, IActionSource source) {
        var found=thaumicenergistics.storage.PortableAspectItems.inspect(item);
        if(found.isEmpty())return Optional.empty();
        var contents=found.get();
        if(contents.aspect()!=null && contents.aspect()!=aspect || contents.filter()!=null && contents.filter()!=aspect)return Optional.empty();
        int space=contents.capacity()-contents.amount();
        if(space<=0)return Optional.empty();
        var key=EssentiaKey.of(aspect);
        // Keep the release's full-free-capacity power check, even for a nearly empty network.
        int available=(int)powered.extract(key,space,Actionable.SIMULATE,source);
        return thaumicenergistics.storage.PortableAspectItems.fill(item,aspect,available);
    }
    private PortableEssentiaTransfers() {}
}
