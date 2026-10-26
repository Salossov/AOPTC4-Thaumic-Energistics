package thaumicenergistics.golems;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.storage.EssentiaKey;
import thaumicenergistics.storage.PoweredEssentiaStorage;

/**
 * Handles AE2 network transactions for wireless golems across Items, Essentia, and Fluids.
 * Preserves SIMULATE / MODULATE contracts, integer rates, and upgrade multipliers from 1.7.10 AIAENetworkGolem.
 */
public final class GolemNetworkBridge {
    public static final int NETWORK_COOLDOWN = 20;

    /** Base rates per order upgrade: 0, 1, 2 upgrades. */
    public static final int[] FLUID_RATES = new int[] { 100, 250, 500 };
    public static final int[] ITEM_RATES = new int[] { 8, 24, 32 };
    public static final int[] ESS_RATES = new int[] { 4, 12, 16 };


    public static int clampOrderUpgrades(int orderUpgrades) {
        if (orderUpgrades > 2) return 2;
        if (orderUpgrades < 0) return 0;
        return orderUpgrades;
    }

    public static int getItemRate(int orderUpgrades, boolean advanced) {
        int rate = ITEM_RATES[clampOrderUpgrades(orderUpgrades)];
        return advanced ? rate * 2 : rate;
    }

    public static int getFluidRate(int orderUpgrades, boolean advanced) {
        int rate = FLUID_RATES[clampOrderUpgrades(orderUpgrades)];
        return advanced ? rate * 2 : rate;
    }

    public static int getEssentiaRate(int orderUpgrades, boolean advanced) {
        int rate = ESS_RATES[clampOrderUpgrades(orderUpgrades)];
        return advanced ? rate * 2 : rate;
    }

    /**
     * Deposits an ItemStack into the linked network. Modifies the input stack count by accepted amount.
     * @return number of items accepted.
     */
    public int depositStack(IGrid grid, ItemStack stack, int maxRate) {
        return depositStack(grid, stack, maxRate, IActionSource.empty());
    }

    public int depositStack(IGrid grid, ItemStack stack, int maxRate, IActionSource actionSource) {
        if (grid == null || stack == null || stack.isEmpty() || maxRate <= 0) {
            return 0;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return 0;
        }

        long depositAmount = Math.min(stack.getCount(), maxRate);
        long inserted = poweredItems(grid, key, depositAmount, actionSource, true);
        if (inserted > 0) {
            stack.shrink((int) inserted);
        }
        return (int) inserted;
    }

    /**
     * Extracts an ItemStack from the linked network matching the target stack type.
     * @return extracted ItemStack, or empty if none.
     */
    public ItemStack extractStack(IGrid grid, ItemStack target, int maxRate) {
        return extractStack(grid, target, maxRate, IActionSource.empty());
    }

    public ItemStack extractStack(IGrid grid, ItemStack target, int maxRate, IActionSource actionSource) {
        if (grid == null || target == null || target.isEmpty() || maxRate <= 0) {
            return ItemStack.EMPTY;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        AEItemKey key = AEItemKey.of(target);
        if (key == null) {
            return ItemStack.EMPTY;
        }

        long extractAmount = Math.min(target.getCount(), maxRate);
        long extracted = poweredItems(grid, key, extractAmount, actionSource, false);
        if (extracted > 0) {
            return key.toStack((int) extracted);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Injects Essentia into the linked network.
     * @return amount of essentia injected.
     */
    public int insertEssentia(IGrid grid, Aspect aspect, int amount, int maxRate) {
        return insertEssentia(grid, aspect, amount, maxRate, IActionSource.empty());
    }

    public int insertEssentia(IGrid grid, Aspect aspect, int amount, int maxRate, IActionSource actionSource) {
        if (grid == null || aspect == null || amount <= 0 || maxRate <= 0) {
            return 0;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        EssentiaKey key = EssentiaKey.of(aspect);
        long amountToInject = Math.min(amount, maxRate);
        long inserted = PoweredEssentiaStorage.of(grid).insert(key, amountToInject, Actionable.MODULATE, actionSource);
        return (int) inserted;
    }

    /**
     * Extracts Essentia from the linked network. Supports SIMULATE and MODULATE.
     * @return amount of essentia extracted.
     */
    public int extractEssentia(IGrid grid, Aspect aspect, int amount, int maxRate, Actionable mode) {
        return extractEssentia(grid, aspect, amount, maxRate, mode, IActionSource.empty());
    }

    public int extractEssentia(IGrid grid, Aspect aspect, int amount, int maxRate, Actionable mode, IActionSource actionSource) {
        if (grid == null || aspect == null || amount <= 0 || maxRate <= 0) {
            return 0;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        EssentiaKey key = EssentiaKey.of(aspect);
        long amountToExtract = Math.min(amount, maxRate);
        // Legacy jar lookup is unpowered; the real transfer costs 0.3 AE/unit.
        long extracted = mode == Actionable.SIMULATE
                ? storage.extract(key, amountToExtract, mode, actionSource)
                : PoweredEssentiaStorage.of(grid).extract(key, amountToExtract, mode, actionSource);
        return (int) extracted;
    }

    /**
     * Extracts fluids from the linked network.
     * @return extracted FluidStack, or empty if none.
     */
    public FluidStack extractFluid(IGrid grid, FluidStack target, int maxRate) {
        return extractFluid(grid, target, maxRate, IActionSource.empty());
    }

    public FluidStack extractFluid(IGrid grid, FluidStack target, int maxRate, IActionSource actionSource) {
        if (grid == null || target == null || target.isEmpty() || maxRate <= 0) {
            return FluidStack.EMPTY;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        AEFluidKey key = AEFluidKey.of(target);
        if (key == null) {
            return FluidStack.EMPTY;
        }

        long amountToExtract = Math.min(target.getAmount(), maxRate);
        // AIAENetworkGolem uses 1 AE/100 mB, independently of modern AE2's quantum.
        var energy = grid.getEnergyService();
        double cost = amountToExtract / 100.0;
        if (energy.extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.CONFIG) < cost) return FluidStack.EMPTY;
        long extracted = storage.extract(key, amountToExtract, Actionable.MODULATE, actionSource);
        energy.extractAEPower(extracted / 100.0, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (extracted > 0) {
            return key.toStack((int) extracted);
        }
        return FluidStack.EMPTY;
    }
    private static long poweredItems(IGrid grid, AEItemKey key, long requested, IActionSource source, boolean insert) {
        var storage = grid.getStorageService().getInventory();
        var energy = grid.getEnergyService();
        long possible = insert ? storage.insert(key, requested, Actionable.SIMULATE, source)
                : storage.extract(key, requested, Actionable.SIMULATE, source);
        double power = energy.extractAEPower(possible, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        long units = Math.min(possible, (long) (power + 0.9));
        if (units <= 0) return 0;
        // Quirk kept: old Platform debits the simulated amount before transferring
        // the power-limited amount, including its +0.9 partial-power rounding.
        energy.extractAEPower(possible, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return insert ? storage.insert(key, units, Actionable.MODULATE, source)
                : storage.extract(key, units, Actionable.MODULATE, source);
    }

}
