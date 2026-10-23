package thaumicenergistics.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Modern StorageCell adapter implementing original 1.1.3.0 essentia cell rules and contracts.
 */
public final class EssentiaCell implements StorageCell {
    public static final ICellHandler HANDLER = new ICellHandler() {
        @Override
        public boolean isCell(ItemStack stack) {
            return stack != null && !stack.isEmpty() && stack.getItem() instanceof EssentiaCellItem;
        }

        @Override
        public StorageCell getCellInventory(ItemStack stack, ISaveProvider saves) {
            return isCell(stack) ? new EssentiaCell(stack, saves) : null;
        }
    };

    private final ItemStack stack;
    private final ISaveProvider saves;
    private final CellTier tier;
    private final java.util.function.BooleanSupplier validOwner;
    private boolean valid = true;

    public EssentiaCell(ItemStack stack, ISaveProvider saves) {
        this(stack, saves, () -> true);
    }

    public EssentiaCell(ItemStack stack, ISaveProvider saves, java.util.function.BooleanSupplier validOwner) {
        Objects.requireNonNull(stack, "stack cannot be null");
        if (!(stack.getItem() instanceof EssentiaCellItem item)) {
            throw new IllegalArgumentException("Not an essentia cell: " + stack);
        }
        this.stack = stack;
        this.saves = saves;
        this.tier = item.tier();
        this.validOwner = Objects.requireNonNull(validOwner);
    }

    public boolean isValid() {
        return valid && !stack.isEmpty() && stack.getItem() instanceof EssentiaCellItem && validOwner.getAsBoolean();
    }

    public void invalidate() {
        this.valid = false;
    }

    public ItemStack getStack() {
        return stack;
    }

    public CellTier getTier() {
        return tier;
    }

    public CellLedger ledger() {
        return new CellLedger(tier, stack.getOrDefault(ThaumicEnergistics.CELL_CONTENTS, CellContents.EMPTY));
    }

    private void save(CellLedger ledger) {
        if (!isValid()) {
            return;
        }
        stack.set(ThaumicEnergistics.CELL_CONTENTS, ledger.contents());
        if (saves != null) {
            saves.saveChanges();
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !isValid()) {
            return 0L;
        }
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (!(what instanceof EssentiaKey key)) {
            return 0L;
        }
        var ledger = ledger();
        long inserted = ledger.insert(key.tag(), amount, mode == Actionable.MODULATE);
        if (inserted > 0 && mode == Actionable.MODULATE) {
            save(ledger);
        }
        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !isValid()) {
            return 0L;
        }
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (!(what instanceof EssentiaKey key)) {
            return 0L;
        }
        var ledger = ledger();
        long extracted = ledger.extract(key.tag(), amount, mode == Actionable.MODULATE);
        if (extracted > 0 && mode == Actionable.MODULATE && !tier.creative()) {
            save(ledger);
        }
        return extracted;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        if (!isValid()) {
            return false;
        }
        return what instanceof EssentiaKey key && ledger().preferred(key.tag());
    }

    @Override
    public void getAvailableStacks(KeyCounter counter) {
        if (!isValid()) {
            return;
        }
        var ledger = ledger();
        if (tier.creative()) {
            ledger.contents().partitions().forEach(tag ->
                    counter.add(EssentiaKey.ofTag(tag), CellLedger.CREATIVE_DISPLAY_AMOUNT));
        } else {
            ledger.contents().amounts().forEach((tag, amount) ->
                    counter.add(EssentiaKey.ofTag(tag), amount));
        }
    }

    @Override
    public CellState getStatus() {
        if (!isValid()) {
            return CellState.ABSENT;
        }
        var ledger = ledger();
        if (tier.creative()) {
            return CellState.TYPES_FULL;
        }
        if (ledger.total() >= tier.capacity()) {
            return CellState.FULL;
        }
        if (ledger.usedTypes() >= tier.types) {
            return CellState.TYPES_FULL;
        }
        return ledger.total() == 0 ? CellState.EMPTY : CellState.NOT_EMPTY;
    }

    public void partitions(List<String> tags) {
        if (!isValid()) {
            return;
        }
        var ledger = ledger();
        ledger.partitions(tags);
        save(ledger);
    }

    @Override
    public double getIdleDrain() {
        return tier.idleDrain;
    }

    @Override
    public boolean canFitInsideCell() {
        return !tier.creative() && isValid() && ledger().total() == 0;
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    @Override
    public void persist() {
        if (isValid() && saves != null) {
            saves.saveChanges();
        }
    }
}
