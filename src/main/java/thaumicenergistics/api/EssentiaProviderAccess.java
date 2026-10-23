package thaumicenergistics.api;

import appeng.api.networking.IGrid;
import java.util.Map;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumcraft.api.aspects.Aspect;

/** Server-thread, read-only boundary between the provider and optional integrations. */
public interface EssentiaProviderAccess {
    BlockEntity getBlockEntity();
    boolean isActive();
    /** Identity of the current grid; null when detached. Never loads a chunk. */
    IGrid providerGrid();
    /** Powered network contents in indivisible essentia units, copied for the caller. */
    Map<Aspect, Long> essentiaSnapshot();
    void addProviderListener(Listener listener);
    void removeProviderListener(Listener listener);

    interface Listener {
        /** The grid identity lets legacy watchers invalidate subscriptions after a grid change. */
        void onEssentiaChanged(IGrid grid, Aspect aspect, long delta);
        void onProviderPowerChange(boolean online);
        void onProviderBroken();
    }
}
