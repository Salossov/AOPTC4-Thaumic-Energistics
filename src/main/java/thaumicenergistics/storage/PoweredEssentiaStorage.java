package thaumicenergistics.storage;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;

/** The original EssentiaMonitor power rules over the modern generic AE2 inventory. */
public final class PoweredEssentiaStorage implements MEStorage {
    public static final double AE_PER_ESSENTIA = 0.3;
    private final MEStorage storage;
    private final IEnergySource energy;
    public PoweredEssentiaStorage(MEStorage storage, IEnergySource energy) { this.storage = storage; this.energy = energy; }
    public static PoweredEssentiaStorage of(IGrid grid) {
        return new PoweredEssentiaStorage(grid.getStorageService().getInventory(), grid.getEnergyService());
    }
    @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (!(what instanceof EssentiaKey) || amount == 0) return 0;
        long accepted = storage.insert(what, amount, Actionable.SIMULATE, source);
        if (energy.extractAEPower(accepted * AE_PER_ESSENTIA, Actionable.SIMULATE, PowerMultiplier.CONFIG) < accepted * AE_PER_ESSENTIA) return 0;
        if (mode == Actionable.MODULATE && accepted > 0) {
            accepted = storage.insert(what, accepted, mode, source);
            energy.extractAEPower(accepted * AE_PER_ESSENTIA, mode, PowerMultiplier.CONFIG);
        }
        return accepted;
    }
    @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (!(what instanceof EssentiaKey) || amount == 0) return 0;
        // Quirk kept from EssentiaMonitor: check power for the entire requested amount first.
        if (energy.extractAEPower(amount * AE_PER_ESSENTIA, Actionable.SIMULATE, PowerMultiplier.CONFIG) < amount * AE_PER_ESSENTIA) return 0;
        long extracted = storage.extract(what, amount, mode, source);
        if (extracted > 0 && mode == Actionable.MODULATE) energy.extractAEPower(extracted * AE_PER_ESSENTIA, mode, PowerMultiplier.CONFIG);
        return extracted;
    }
    @Override public void getAvailableStacks(KeyCounter counter) { storage.getAvailableStacks(counter); }
    @Override public Component getDescription() { return storage.getDescription(); }
}
