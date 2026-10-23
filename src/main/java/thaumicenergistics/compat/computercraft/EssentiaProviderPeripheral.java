package thaumicenergistics.compat.computercraft;

import appeng.api.networking.IGrid;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.EssentiaProviderAccess;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern CC:Tweaked peripheral for the Thaumic Energistics Essentia Provider.
 * Conforms to the {@link EssentiaProviderAccess} contract and implements CC:Tweaked {@link IPeripheral}.
 */
public class EssentiaProviderPeripheral implements IPeripheral, EssentiaProviderAccess.Listener {
    public static final String PERIPHERAL_TYPE = "EssentiaProvider";
    public static final String EVENT_NAME = "essentia";

    private static final Map<String, Aspect> aspectNameReverseMap = new ConcurrentHashMap<>();

    private final WeakReference<Level> levelRef;
    private final ResourceKey<Level> levelDimension;
    private final BlockPos pos;
    private final EssentiaWatcher watcher;
    private final Set<IComputerAccess> attachedComputers = Collections.synchronizedSet(new HashSet<>());
    private boolean isRegisteredAsBlockListener = false;

    public EssentiaProviderPeripheral(EssentiaProviderAccess access) {
        BlockEntity be = access.getBlockEntity();
        Level level = be != null ? be.getLevel() : null;
        this.levelRef = new WeakReference<>(level);
        this.levelDimension = level != null ? level.dimension() : null;
        this.pos = be != null ? be.getBlockPos() : BlockPos.ZERO;
        this.watcher = new EssentiaWatcher(this);
        ensureListenerRegistered(access);
    }

    public EssentiaProviderPeripheral(Level level, BlockPos pos) {
        this.levelRef = new WeakReference<>(level);
        this.levelDimension = level != null ? level.dimension() : null;
        this.pos = pos != null ? pos : BlockPos.ZERO;
        this.watcher = new EssentiaWatcher(this);
        getProvider();
    }

    @Override
    public String getType() {
        return PERIPHERAL_TYPE;
    }

    @Nullable
    @Override
    public Object getTarget() {
        Level level = this.levelRef != null ? this.levelRef.get() : null;
        if (level == null || !level.hasChunkAt(this.pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(this.pos);
        return (be != null && !be.isRemoved()) ? be : null;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        if (this == other) return true;
        if (!(other instanceof EssentiaProviderPeripheral o)) return false;
        return Objects.equals(this.pos, o.pos) && Objects.equals(this.levelDimension, o.levelDimension);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EssentiaProviderPeripheral o)) return false;
        return Objects.equals(this.pos, o.pos) && Objects.equals(this.levelDimension, o.levelDimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.pos, this.levelDimension);
    }

    @Override
    public void attach(IComputerAccess computer) {
        this.attachedComputers.add(computer);
        ensureListenerRegistered(getProvider());
    }

    @Override
    public void detach(IComputerAccess computer) {
        this.attachedComputers.remove(computer);
        this.watcher.removeComputerWatcher(computer);
        try {
            sendEvent(computer, CCEvents.Detached);
        } catch (Exception ignored) {
        }
        if (this.attachedComputers.isEmpty()) {
            EssentiaProviderAccess provider = getProvider();
            if (provider != null) {
                provider.removeProviderListener(this);
            }
            this.isRegisteredAsBlockListener = false;
        }
    }

    @LuaFunction(mainThread = true)
    public final boolean isOnline() {
        return this.isProviderAvailable();
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getAspects() {
        if (!this.isProviderAvailable()) {
            return MethodResult.of();
        }
        EssentiaProviderAccess provider = this.getProvider();
        if (provider == null) {
            return MethodResult.of();
        }
        Map<Aspect, Long> snapshot = provider.essentiaSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return MethodResult.of();
        }

        Object[] ccList = new Object[snapshot.size() * 2];
        int index = 0;
        for (Map.Entry<Aspect, Long> entry : snapshot.entrySet()) {
            Aspect aspect = entry.getKey();
            long amount = entry.getValue() != null ? entry.getValue() : 0L;
            ccList[index] = aspect != null ? aspect.getName() : "";
            ccList[index + 1] = amount;
            index += 2;
        }
        return MethodResult.of(ccList);
    }

    @LuaFunction(mainThread = true)
    public final MethodResult getAmount(IArguments arguments) throws LuaException {
        EssentiaProviderAccess provider = this.getProvider();
        List<Aspect> aspects = this.getAspectsFromArguments(arguments);
        Object[] results = new Object[aspects.size()];
        boolean providerActive = provider != null && provider.isActive();
        Map<Aspect, Long> snapshot = providerActive ? provider.essentiaSnapshot() : null;

        for (int i = 0; i < results.length; i++) {
            if (providerActive && snapshot != null) {
                results[i] = snapshot.getOrDefault(aspects.get(i), 0L);
            } else {
                results[i] = 0L;
            }
        }
        return MethodResult.of(results);
    }

    @LuaFunction(mainThread = true)
    public final boolean registerAsWatcher(IComputerAccess computer, IArguments arguments) throws LuaException {
        if (!this.isProviderAvailable()) {
            return false;
        }

        if (arguments != null && arguments.count() > 0) {
            List<Aspect> aspectList = this.getAspectsFromArguments(arguments);
            HashSet<Aspect> aspects = new HashSet<>(aspectList);
            this.watcher.addComputerWatch(computer, aspects);
        } else {
            this.watcher.addComputerWatch(computer, null);
        }
        return true;
    }

    @LuaFunction(mainThread = true)
    public final boolean unregisterAsWatcher(IComputerAccess computer, IArguments arguments) {
        this.watcher.removeComputerWatcher(computer);
        return true;
    }

    @LuaFunction
    public final MethodResult help() {
        return MethodResult.of(CCHelpObject.INSTANCE);
    }

    @Override
    public void onEssentiaChanged(IGrid grid, Aspect aspect, long delta) {
        this.watcher.onEssentiaChanged(grid, aspect, delta);
    }

    @Override
    public void onProviderPowerChange(boolean online) {
        List<IComputerAccess> bad = null;
        synchronized (this.attachedComputers) {
            for (IComputerAccess computer : this.attachedComputers) {
                try {
                    sendEvent(computer, CCEvents.PowerChange, online);
                } catch (Exception ex) {
                    if (bad == null) bad = new ArrayList<>();
                    bad.add(computer);
                }
            }
            if (bad != null) {
                for (IComputerAccess computer : bad) {
                    this.attachedComputers.remove(computer);
                    this.watcher.removeComputerWatcher(computer);
                }
            }
        }
    }

    @Override
    public void onProviderBroken() {
        this.isRegisteredAsBlockListener = false;
        List<IComputerAccess> bad = null;
        synchronized (this.attachedComputers) {
            for (IComputerAccess computer : this.attachedComputers) {
                try {
                    sendEvent(computer, CCEvents.PowerChange, false);
                } catch (Exception ex) {
                    if (bad == null) bad = new ArrayList<>();
                    bad.add(computer);
                }
            }
            if (bad != null) {
                for (IComputerAccess computer : bad) {
                    this.attachedComputers.remove(computer);
                }
            }
        }
        this.watcher.onProviderBroken();
    }

    void sendEvent(IComputerAccess computer, CCEvents event, Object... additionalData) {
        Object[] args = new Object[additionalData == null ? 1 : 1 + additionalData.length];
        args[0] = event.eventID;
        if (additionalData != null && additionalData.length > 0) {
            System.arraycopy(additionalData, 0, args, 1, additionalData.length);
        }
        computer.queueEvent(EVENT_NAME, args);
    }

    EssentiaProviderAccess getProvider() {
        Level level = this.levelRef != null ? this.levelRef.get() : null;
        if (level == null || !level.hasChunkAt(this.pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(this.pos);
        if (be instanceof EssentiaProviderAccess access && !be.isRemoved()) {
            ensureListenerRegistered(access);
            return access;
        }
        return null;
    }

    private synchronized void ensureListenerRegistered(EssentiaProviderAccess provider) {
        if (!this.isRegisteredAsBlockListener && provider != null) {
            provider.addProviderListener(this);
            this.isRegisteredAsBlockListener = true;
        }
    }

    private boolean isProviderAvailable() {
        EssentiaProviderAccess provider = this.getProvider();
        return provider != null && provider.isActive();
    }

    private Aspect getAspectFromName(String name) {
        if (name == null) return null;
        String aspectName = name.toLowerCase(Locale.ROOT);
        Aspect cached = aspectNameReverseMap.get(aspectName);
        if (cached != null) {
            return cached;
        }

        Aspect byTag = Aspect.getAspect(aspectName);
        if (byTag != null) {
            aspectNameReverseMap.put(aspectName, byTag);
            return byTag;
        }

        for (Aspect searchAspect : Aspect.all().values()) {
            if (searchAspect.getName().equalsIgnoreCase(aspectName) || searchAspect.getTag().equalsIgnoreCase(aspectName)) {
                aspectNameReverseMap.put(aspectName, searchAspect);
                return searchAspect;
            }
        }

        return null;
    }

    private List<Aspect> getAspectsFromArguments(IArguments arguments) throws LuaException {
        if (arguments == null || arguments.count() == 0) {
            throw new LuaException("Expected argument: <string>AspectName");
        }

        List<Aspect> results = new ArrayList<>(arguments.count());
        for (int i = 0; i < arguments.count(); i++) {
            Object arg = arguments.get(i);
            if (!(arg instanceof String str)) {
                throw new LuaException(String.format("Invalid argument '%s'", arg));
            }

            Aspect aspect = this.getAspectFromName(str);
            if (aspect == null) {
                throw new LuaException(String.format("Invalid aspect name '%s'", arg));
            }

            results.add(aspect);
        }

        return results;
    }

    public EssentiaWatcher getWatcher() {
        return this.watcher;
    }

    public Set<IComputerAccess> getAttachedComputers() {
        return Collections.unmodifiableSet(this.attachedComputers);
    }
}
