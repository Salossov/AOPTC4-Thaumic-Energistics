package thaumicenergistics.golems;

import java.util.*;
import appeng.api.config.Actionable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import thaumcraft.api.addon.AddonRegistrationEvent;
import thaumcraft.api.aspects.*;
import thaumcraft.api.entities.GolemWorkApi;
import thaumcraft.api.entities.GolemWorkApi.*;
import thaumicenergistics.ThaumicEnergistics;

/** Release AIAENetworkGolem and four WiFi jobs, using only the public native work contract. */
public final class WirelessGolemWork {
    private static final ResourceLocation ALEMBIC = ResourceLocation.parse("thaumcraft:arcane_alembic");
    public static void initialize() {
        AddonRegistrationEvent.BUS.addListener((AddonRegistrationEvent event) ->
                GolemWorkApi.registerExtension(ThaumicEnergistics.id("wireless_backpack"), WirelessGolemWork::create));
    }
    private static Extension create(Context context) {
        if (!GolemBackpackManager.hasBackpack(context.entity())) return null;
        var state = context.snapshot();
        NetworkGoal goal = switch (state.core()) {
            case 0 -> new Fill(context, state);
            case 2 -> new Gather(context, state);
            case 5 -> new Liquid(context, state);
            case 6 -> new Essentia(context, state);
            default -> null;
        };
        if (goal == null) return null;
        int priority = switch (state.core()) { case 2 -> 1; case 0 -> 3; default -> 2; };
        return new Extension() {
            @Override public List<PrioritizedGoal> goals() { return List.of(new PrioritizedGoal(priority, goal)); }
            @Override public boolean hasItemDestination() {
                return state.core() == 2 && GolemBackpackManager.getActiveConnection(context.entity()) != null;
            }
        };
    }
    private abstract static class NetworkGoal extends Goal {
        final Context context;
        final int order;
        final boolean advanced;
        private int cooldown;
        private boolean performed;
        NetworkGoal(Context context, Snapshot state) {
            this.context = context; order = state.upgrade(4); advanced = state.advanced();
            // Legacy EntityAIBase leaves mutex bits at zero; network jobs do not own movement.
        }
        abstract boolean needs(Snapshot state);
        abstract void transfer(Snapshot state);
        @Override public final boolean canUse() {
            if (cooldown > 0) { cooldown--; return false; }
            cooldown = GolemNetworkBridge.NETWORK_COOLDOWN;
            var state = context.snapshot();
            return !state.inactive() && GolemBackpackManager.getActiveConnection(context.entity()) != null && needs(state);
        }
        @Override public final boolean canContinueToUse() { return false; }
        @Override public final boolean requiresUpdateEveryTick() { return true; }
        @Override public final void start() { performed = false; }
        @Override public final void tick() {
            if (performed) return;
            performed = true;
            var state = context.snapshot();
            if (!state.inactive() && GolemBackpackManager.getActiveConnection(context.entity()) != null && needs(state)) transfer(state);
        }
    }
    private static final class Gather extends NetworkGoal {
        Gather(Context context, Snapshot state) { super(context, state); }
        @Override boolean needs(Snapshot state) { return !state.cargo().item().isEmpty(); }
        @Override void transfer(Snapshot state) {
            if (context.exchange(state, cargo -> {
                var carried = cargo.item();
                GolemHooksBridge.executeGather(context.entity(), carried, order, advanced);
                return new Cargo(carried, cargo.fluid(), cargo.aspect(), cargo.essentia());
            }, false)) {
                var after = context.snapshot();
                if (after.cargo().item().isEmpty()) context.beginAction(after);
            }
        }
    }
    private static final class Fill extends NetworkGoal {
        private ItemStack wanted = ItemStack.EMPTY;
        Fill(Context context, Snapshot state) { super(context, state); }
        @Override boolean needs(Snapshot state) {
            wanted = ItemStack.EMPTY;
            if (!state.cargo().item().isEmpty()) return false;
            var demand = context.itemDemand();
            if (!demand.isEmpty()) wanted = demand.getLast().copy();
            return !wanted.isEmpty();
        }
        @Override void transfer(Snapshot state) {
            var request = wanted.copy();
            context.exchange(state, cargo -> {
                var extracted = GolemHooksBridge.executeFill(context.entity(), request, order, advanced);
                return extracted.isEmpty() ? cargo : new Cargo(extracted, cargo.fluid(), cargo.aspect(), cargo.essentia());
            }, false);
        }
    }
    private static final class Liquid extends NetworkGoal {
        private FluidStack wanted = FluidStack.EMPTY;
        Liquid(Context context, Snapshot state) { super(context, state); }
        @Override boolean needs(Snapshot state) {
            wanted = FluidStack.EMPTY;
            var demand = context.fluidDemand();
            if (demand.size() != 1) return false;
            var carried = state.cargo().fluid(); var requested = demand.getFirst();
            if (!carried.isEmpty() && !FluidStack.isSameFluidSameComponents(carried, requested)) return false;
            int remaining = 1000 - carried.getAmount();
            if (remaining > 0) wanted = requested.copyWithAmount(remaining);
            return !wanted.isEmpty();
        }
        @Override void transfer(Snapshot state) {
            var request = wanted.copy();
            context.exchange(state, cargo -> {
                var extracted = GolemHooksBridge.executeLiquid(context.entity(), request, order, advanced);
                if (extracted.isEmpty()) return cargo;
                var held = cargo.fluid();
                if (held.isEmpty()) held = extracted; else held.grow(extracted.getAmount());
                return new Cargo(cargo.item(), held, cargo.aspect(), cargo.essentia());
            }, false);
        }
    }
    private static final class Essentia extends NetworkGoal {
        private final Set<BlockPos> checked = new HashSet<>();
        private BlockPos target;
        private BlockEntity targetIdentity;
        private double targetDistance, rangeSquared;
        private boolean targetVoid, fillMode;
        Essentia(Context context, Snapshot state) { super(context, state); }
        private ServerLevel world() { return (ServerLevel) context.entity().level(); }
        private IAspectContainer jar(BlockPos pos) {
            if (pos == null || !world().hasChunkAt(pos)) return null;
            var container = world().getCapability(EssentiaCapabilities.CONTAINER, pos, null);
            return container != null && container.getJarType() != IAspectContainer.JarType.NONE ? container : null;
        }
        private Aspect aspect(IAspectContainer jar) {
            var filter = jar.getAspectFilter();
            if (filter != null) return filter;
            for (var entry : jar.getAspects().getAspects()) if (jar.containerContains(entry) > 0) return entry;
            return null;
        }
        private boolean fillMode(Snapshot state) {
            var source = state.source();
            if (!world().hasChunkAt(source)) return false;
            if (world().getCapability(EssentiaCapabilities.TRANSPORT, source, null) != null) return false;
            for (int height = 1; height < 6; height++) {
                var pos = source.above(height);
                if (world().hasChunkAt(pos) && BuiltInRegistries.BLOCK.getKey(world().getBlockState(pos).getBlock()).equals(ALEMBIC)) return false;
            }
            return true;
        }
        @Override boolean needs(Snapshot state) {
            if (!world().hasChunkAt(state.source())) return false;
            fillMode = fillMode(state);
            if (!fillMode) return state.cargo().essentia() > 0 && context.entity().getNavigation().isDone();
            if (state.cargo().essentia() > 0) return false;
            findJar(state);
            return target != null;
        }
        private void findJar(Snapshot state) {
            var cached = jar(target);
            if (cached != null && world().getBlockEntity(target) == targetIdentity) {
                var wanted = aspect(cached);
                if (wanted != null && (targetVoid || cached.getAspects().visSize() < 64)
                        && GolemHooksBridge.executeEssentiaExtract(context.entity(), wanted, 1, order, advanced, Actionable.SIMULATE) > 0) return;
            }
            target = null; targetIdentity = null; targetDistance = Double.MAX_VALUE; targetVoid = false;
            rangeSquared = state.range() * state.range(); checked.clear();
            for (var marker : state.markers()) {
                if (!marker.dimension().equals(world().dimension()) || jar(marker.position()) == null) continue;
                select(marker.position(), state.home()); connected(marker.position(), state.home());
            }
            checked.clear();
        }
        private void connected(BlockPos pos, BlockPos home) {
            if (checked.size() >= 200) return;
            for (var face : Direction.values()) {
                var next = pos.relative(face);
                if (jar(next) != null && select(next, home) > 2) connected(next, home);
            }
        }
        private int select(BlockPos pos, BlockPos home) {
            if (!checked.add(pos)) return 2;
            double distance = pos.getCenter().distanceToSqr(home.getX(), home.getY(), home.getZ());
            if (distance > rangeSquared) return 1;
            var jar = jar(pos); if (jar == null) return 1;
            boolean isVoid = jar.getJarType() == IAspectContainer.JarType.VOID;
            // Literal source selection order, including its normal/void distance asymmetry.
            if ((!targetVoid || isVoid) && distance > targetDistance) return 1;
            if (aspect(jar) == null || !isVoid && jar.getAspects().visSize() >= 64) return 3;
            target = pos.immutable(); targetIdentity = world().getBlockEntity(pos);
            targetDistance = distance; targetVoid = isVoid; return 4;
        }
        @Override void transfer(Snapshot state) {
            if (fillMode) {
                var jar = jar(target);
                if (jar == null || world().getBlockEntity(target) != targetIdentity) return;
                var wanted = aspect(jar); if (wanted == null) { target = null; return; }
                int amount = targetVoid ? 64 : 64 - jar.getAspects().visSize();
                if (amount < 1) { target = null; return; }
                context.exchange(state, cargo -> {
                    int extracted = GolemHooksBridge.executeEssentiaExtract(context.entity(), wanted, amount, order, advanced, Actionable.MODULATE);
                    return extracted > 0 ? new Cargo(cargo.item(), cargo.fluid(), wanted, extracted) : cargo;
                }, false);
            } else if (context.entity().getNavigation().isDone()) {
                context.exchange(state, cargo -> {
                    int accepted = GolemHooksBridge.executeEssentiaInsert(context.entity(), cargo.aspect(), cargo.essentia(), order, advanced);
                    return new Cargo(cargo.item(), cargo.fluid(), cargo.aspect(), cargo.essentia() - accepted);
                }, false);
            }
        }
    }
    private WirelessGolemWork() {}
}
