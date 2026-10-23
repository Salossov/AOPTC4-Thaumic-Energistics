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
        var found=EssentiaItemApi.inspect(item);
        if(found.isEmpty())return Optional.empty();
        var contents=found.get();
        if(contents.amount()==0 || required!=null && contents.aspect()!=required)return Optional.empty();
        var key=EssentiaKey.of(contents.aspect());
        int accepted=(int)powered.insert(key,contents.amount(),Actionable.SIMULATE,source);
        // Approved fix: an eight-unit phial cannot be consumed for a partial deposit.
        var quote=EssentiaItemApi.drain(item,contents.aspect(),accepted);
        if(quote.isEmpty())return Optional.empty();
        int actual=(int)powered.insert(key,quote.get().actualUnits(),Actionable.MODULATE,source);
        var result=EssentiaItemApi.drain(item,contents.aspect(),actual);
        if(result.isEmpty() && actual>0) {
            // Same-thread endpoints normally honour their simulation. Compensate if not.
            if(raw.extract(key,actual,Actionable.MODULATE,source)!=actual)
                throw new IllegalStateException("Portable deposit changed during transfer; rollback failed");
        }
        return result;
    }

    public static Optional<EssentiaItemApi.TransferResult> fill(ItemStack item, Aspect aspect,
            MEStorage powered, MEStorage raw, IActionSource source) {
        var found=EssentiaItemApi.inspect(item);
        if(found.isEmpty())return Optional.empty();
        var contents=found.get();
        if(contents.aspect()!=null && contents.aspect()!=aspect || contents.filter()!=null && contents.filter()!=aspect)return Optional.empty();
        int space=contents.capacity()-contents.amount();
        if(space<=0)return Optional.empty();
        var key=EssentiaKey.of(aspect);
        // Keep the release's full-free-capacity power check, even for a nearly empty network.
        int available=(int)powered.extract(key,space,Actionable.SIMULATE,source);
        var quote=EssentiaItemApi.fill(item,aspect,available);
        if(quote.isEmpty())return Optional.empty();
        int actual=(int)powered.extract(key,quote.get().actualUnits(),Actionable.MODULATE,source);
        var result=EssentiaItemApi.fill(item,aspect,actual);
        if(result.isEmpty() && actual>0) {
            if(raw.insert(key,actual,Actionable.MODULATE,source)!=actual)
                throw new IllegalStateException("Portable fill changed during transfer; rollback failed");
        }
        return result;
    }
    private PortableEssentiaTransfers() {}
}
