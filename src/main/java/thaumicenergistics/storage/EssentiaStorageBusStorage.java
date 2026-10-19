package thaumicenergistics.storage;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.misc.CondenserBlockEntity;
import appeng.helpers.InterfaceLogicHost;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.network.chat.Component;
import thaumicenergistics.parts.EssentiaStorageBusPart;

/** The original container/interface/condenser duality, adapted to generic AE2 storage. */
public final class EssentiaStorageBusStorage implements MEStorage {
    private enum Kind { CONTAINER, INTERFACE, CONDENSER }
    private record Target(Kind kind, MEStorage storage, CondenserBlockEntity condenser, IGrid grid) {}
    private static final ThreadLocal<Set<IGrid>> PATH = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));
    private final EssentiaStorageBusPart part;
    private boolean entered;
    private IGrid enteredGrid;
    public EssentiaStorageBusStorage(EssentiaStorageBusPart part) { this.part = part; }

    private Target target() {
        var level = part.getLevel();
        var pos = part.getBlockEntity().getBlockPos().relative(part.getSide());
        if (!level.hasChunkAt(pos)) return null;
        var tile = level.getBlockEntity(pos);
        if (tile instanceof CondenserBlockEntity condenser) return new Target(Kind.CONDENSER,null,condenser,null);
        var container = ContainerStorage.at(level,pos,part.allowVoid());
        if (container != null) return new Target(Kind.CONTAINER,container,null,null);
        Object facing = tile;
        if (tile instanceof IPartHost host) facing = host.getPart(part.getSide().getOpposite());
        if (facing instanceof InterfaceLogicHost host) {
            var node = host.getInterfaceLogic().getActionableNode();
            var storage = level.getCapability(AECapabilities.ME_STORAGE,pos,part.getSide().getOpposite());
            if (node != null && node.isActive() && storage != null) return new Target(Kind.INTERFACE,storage,null,node.getGrid());
        }
        return null;
    }

    private boolean enter() {
        if (entered || !part.getMainNode().isActive()) return false;
        var grid = part.getMainNode().getGrid();
        if (grid == null || !PATH.get().add(grid)) return false;
        enteredGrid = grid; entered = true; return true;
    }
    private void leave() {
        var path = PATH.get(); path.remove(enteredGrid); enteredGrid = null;
        if (path.isEmpty()) PATH.remove();
        entered = false;
    }
    private boolean recursive(Target target) { return target.grid != null && PATH.get().contains(target.grid); }

    private long transfer(AEKey what, long amount, Actionable mode, IActionSource source, boolean inserting) {
        MEStorage.checkPreconditions(what,amount,mode,source);
        if (!(what instanceof EssentiaKey key) || amount == 0 || !enter()) return 0;
        try {
            var target = target();
            if (target == null || recursive(target)) return 0;
            if (target.kind == Kind.INTERFACE) {
                // Quirk kept: original interface handler ignores filter/inverter and has no per-unit debit.
                long actual = inserting ? target.storage.insert(key,amount,mode,source) : target.storage.extract(key,amount,mode,source);
                if (actual > 0 && mode == Actionable.MODULATE) part.invalidateCache();
                return actual;
            }
            if (!part.allows(key)) return 0;
            if (target.kind == Kind.CONDENSER) {
                if (!inserting) return 0;
                // Original: 500 fluid units/essentia divided by legacy condenser's 500 = 1 matter/unit.
                // It accepts and voids input even when the condenser cannot retain additional matter.
                if (mode == Actionable.MODULATE) target.condenser.addPower((double)amount);
                return amount;
            }
            long possible = inserting ? target.storage.insert(key,amount,Actionable.SIMULATE,source)
                    : target.storage.extract(key,amount,Actionable.SIMULATE,source);
            if (possible <= 0) return 0;
            var energy = part.getMainNode().getGrid().getEnergyService();
            double cost = possible * PoweredEssentiaStorage.AE_PER_ESSENTIA;
            if (energy.extractAEPower(cost,Actionable.SIMULATE,PowerMultiplier.CONFIG) < cost) return 0;
            if (mode == Actionable.SIMULATE) return possible;
            long actual = inserting ? target.storage.insert(key,possible,mode,source) : target.storage.extract(key,possible,mode,source);
            if (actual > 0) { energy.extractAEPower(actual * PoweredEssentiaStorage.AE_PER_ESSENTIA,mode,PowerMultiplier.CONFIG); part.invalidateCache(); }
            return actual;
        } finally { leave(); }
    }
    @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) { return transfer(what,amount,mode,source,true); }
    @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) { return transfer(what,amount,mode,source,false); }
    @Override public void getAvailableStacks(KeyCounter out) {
        if (!enter()) return;
        try {
            var target = target();
            if (target == null || recursive(target) || target.kind == Kind.CONDENSER) return;
            var contents = target.storage.getAvailableStacks();
            for (var entry : contents) if (entry.getKey() instanceof EssentiaKey key) {
                // Quirk kept: original container display ignores inversion, unlike transfer permission.
                if (target.kind == Kind.INTERFACE || !part.hasFilters() || part.listed(key)) out.add(key,entry.getLongValue());
            }
        } finally { leave(); }
    }
    @Override public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return what instanceof EssentiaKey key && part.listed(key);
    }
    @Override public Component getDescription() { return part.getName(); }
}
