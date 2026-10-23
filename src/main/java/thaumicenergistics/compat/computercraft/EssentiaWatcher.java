package thaumicenergistics.compat.computercraft;

import appeng.api.networking.IGrid;
import dan200.computercraft.api.peripheral.IComputerAccess;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.EssentiaProviderAccess;

import java.util.*;

/**
 * State machine managing computer watchers for the EssentiaProvider peripheral.
 * Concurrency is synchronized on {@code threadLock} matching 1.7.10 behavior,
 * with event dispatches performed outside the lock to prevent deadlock with CC's queue.
 */
public final class EssentiaWatcher {
    private final EssentiaProviderPeripheral peripheral;
    private final Map<IComputerAccess, Boolean> computerWatchers = new HashMap<>();
    private final Map<Aspect, Set<IComputerAccess>> aspectWatchers = new HashMap<>();
    private final Set<IComputerAccess> anyWatchers = new HashSet<>();
    private final Object threadLock = new Object();
    private boolean isReceiving = false;
    private IGrid registeredGrid = null;

    public EssentiaWatcher(EssentiaProviderPeripheral peripheral) {
        this.peripheral = peripheral;
    }

    /**
     * Registers a computer to watch essentia changes.
     * Preserves original 1.7.10 ThE quirk: if the computer was already registered for specific aspects
     * (Boolean.TRUE), it clears old watches before adding new ones; but if it was registered for ANY
     * (Boolean.FALSE), the original code did not clear anyWatchers.
     */
    public void addComputerWatch(IComputerAccess computer, Set<Aspect> aspectsOfInterest) {
        EssentiaProviderAccess provider = this.peripheral.getProvider();
        synchronized (this.threadLock) {
            if (this.computerWatchers.containsKey(computer) && Boolean.TRUE.equals(this.computerWatchers.get(computer))) {
                this.removeComputerWatcher(computer);
            }

            boolean specificWatches = false;
            if (aspectsOfInterest != null && !aspectsOfInterest.isEmpty()) {
                specificWatches = true;
                for (Aspect watchAspect : aspectsOfInterest) {
                    this.aspectWatchers.computeIfAbsent(watchAspect, k -> new HashSet<>()).add(computer);
                }
            } else {
                this.anyWatchers.add(computer);
            }

            this.computerWatchers.put(computer, specificWatches);

            if (provider != null) {
                this.registeredGrid = provider.providerGrid();
                this.isReceiving = (this.registeredGrid != null);
            }
        }
    }

    /**
     * Unregisters a computer from all watches.
     * Matches original 1.7.10 quirk: completely clears all watches for this computer regardless of arguments.
     */
    public void removeComputerWatcher(IComputerAccess computer) {
        synchronized (this.threadLock) {
            if (this.computerWatchers.containsKey(computer)) {
                if (Boolean.TRUE.equals(this.computerWatchers.get(computer))) {
                    for (Set<IComputerAccess> watchSet : this.aspectWatchers.values()) {
                        watchSet.remove(computer);
                    }
                } else {
                    this.anyWatchers.remove(computer);
                }
                this.computerWatchers.remove(computer);
                if (this.computerWatchers.isEmpty()) {
                    this.isReceiving = false;
                }
            }
        }
    }

    public void onProviderBroken() {
        synchronized (this.threadLock) {
            this.anyWatchers.clear();
            this.aspectWatchers.clear();
            this.computerWatchers.clear();
            this.isReceiving = false;
            this.registeredGrid = null;
        }
    }

    public void onEssentiaChanged(IGrid grid, Aspect aspect, long delta) {
        EssentiaProviderAccess provider = this.peripheral.getProvider();
        if (provider == null) {
            this.onProviderBroken();
            return;
        }
        if (!provider.isActive()) {
            return;
        }

        List<IComputerAccess> targets = new ArrayList<>();
        synchronized (this.threadLock) {
            if (!this.isReceiving || grid == null || grid != this.registeredGrid) {
                // Requirement 14: Grid identity verification:
                // Subscriptions to old grid become invalid after grid change.
                return;
            }

            if (!this.anyWatchers.isEmpty()) {
                targets.addAll(this.anyWatchers);
            }

            Set<IComputerAccess> specific = this.aspectWatchers.get(aspect);
            if (specific != null && !specific.isEmpty()) {
                for (IComputerAccess computer : specific) {
                    if (!this.anyWatchers.contains(computer)) {
                        targets.add(computer);
                    }
                }
            }
        }

        if (targets.isEmpty()) return;

        Set<IComputerAccess> invalidWatchers = null;
        Object[] ccChange = new Object[] { aspect.getName(), delta };

        for (IComputerAccess computer : targets) {
            try {
                this.peripheral.sendEvent(computer, CCEvents.AspectUpdate, ccChange);
            } catch (Exception ex) {
                if (invalidWatchers == null) invalidWatchers = new HashSet<>();
                invalidWatchers.add(computer);
            }
        }

        // Resilience watcher delivery: Safely unregister any bad computers without affecting others.
        if (invalidWatchers != null) {
            synchronized (this.threadLock) {
                for (IComputerAccess badComputer : invalidWatchers) {
                    this.removeComputerWatcher(badComputer);
                }
            }
        }
    }

    public boolean isWatching(IComputerAccess computer) {
        synchronized (this.threadLock) {
            return this.computerWatchers.containsKey(computer);
        }
    }

    public boolean hasWatchers() {
        synchronized (this.threadLock) {
            return !this.computerWatchers.isEmpty();
        }
    }

    public IGrid getRegisteredGrid() {
        synchronized (this.threadLock) {
            return this.registeredGrid;
        }
    }
}
