package thaumicenergistics.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;

/** Server-thread transfer steps shared by ThE buses; no caller decrements a requested amount blindly. */
public final class EssentiaTransfers {
    public static long importFrom(ContainerStorage container, MEStorage network, EssentiaKey aspect, long limit, IActionSource source) {
        long available = container.extract(aspect, limit, Actionable.SIMULATE, source);
        long accepted = network.insert(aspect, available, Actionable.MODULATE, source);
        if (accepted == 0) return 0;
        long removed = container.extract(aspect, accepted, Actionable.MODULATE, source);
        if (removed != accepted) {
            // Defensive compensation if a container invalidated its own extraction promise.
            long rolledBack = network.extract(aspect, accepted - removed, Actionable.MODULATE, source);
            if (rolledBack != accepted - removed) throw new IllegalStateException("Container changed during essentia import; rollback failed");
        }
        // User-approved fix: original PartEssentiaImportBus removed limit, even after partial acceptance.
        return removed;
    }
    public static long exportTo(MEStorage network, ContainerStorage container, EssentiaKey aspect, long limit, IActionSource source) {
        long room = container.insert(aspect, limit, Actionable.SIMULATE, source);
        long extracted = network.extract(aspect, room, Actionable.MODULATE, source);
        if (extracted == 0) return 0;
        long inserted = container.insert(aspect, extracted, Actionable.MODULATE, source);
        if (inserted != extracted) {
            long returned = network.insert(aspect, extracted - inserted, Actionable.MODULATE, source);
            if (returned != extracted - inserted) throw new IllegalStateException("Container changed during essentia export; rollback failed");
        }
        return inserted;
    }
    private EssentiaTransfers() {}
}
