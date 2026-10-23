package thaumicenergistics.storage;

import appeng.api.config.*;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;

/** EssentiaPassThroughMonitor checks the full insertion request before the raw
 * inventory, unlike ordinary EssentiaMonitor. It never bills grid energy. */
public final class WirelessEssentiaStorage implements MEStorage {
    private final MEStorage raw;
    private final IEnergySource battery;
    private final PoweredEssentiaStorage extraction;
    public WirelessEssentiaStorage(MEStorage raw,IEnergySource battery){this.raw=raw;this.battery=battery;extraction=new PoweredEssentiaStorage(raw,battery);}
    @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source){
        MEStorage.checkPreconditions(key,amount,mode,source);if(!(key instanceof EssentiaKey)||amount==0)return 0;
        if(battery.extractAEPower(.3*amount,Actionable.SIMULATE,PowerMultiplier.CONFIG)<.3*amount)return 0;
        long accepted=raw.insert(key,amount,mode,source);
        if(accepted>0&&mode==Actionable.MODULATE)battery.extractAEPower(.3*accepted,mode,PowerMultiplier.CONFIG);
        return accepted;
    }
    @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource source){return extraction.extract(key,amount,mode,source);}
    @Override public void getAvailableStacks(KeyCounter counter){raw.getAvailableStacks(counter);}
    @Override public Component getDescription(){return raw.getDescription();}
}
