package thaumicenergistics.menu;

import appeng.api.stacks.GenericStack;
import appeng.api.util.DimensionalBlockPos;
import appeng.util.Platform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectItem;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.blocks.CellWorkbenchBlockEntity;
import thaumicenergistics.storage.CellContents;
import thaumicenergistics.storage.EssentiaCellItem;
import thaumicenergistics.storage.EssentiaKey;
import thaumicenergistics.storage.PortableAspectItems;

public final class CellWorkbenchMenu extends AbstractContainerMenu {
    public static final int FILTER_COUNT = 63, CELL_SLOT = 63, PARTITION_CONTENTS = 0, CLEAR = 1;
    private final Player owner;
    private final CellWorkbenchBlockEntity workbench;
    private final SimpleContainer ghosts = new SimpleContainer(FILTER_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 0;
        }
    };

    public CellWorkbenchMenu(int id, Inventory inventory, CellWorkbenchBlockEntity workbench) {
        super(ThaumicEnergistics.WORKBENCH_MENU.get(), id);
        this.owner = inventory.player;
        this.workbench = workbench;
        for (int i = 0; i < FILTER_COUNT; i++) {
            addSlot(new Slot(ghosts, i, 8 + (i % 9) * 18, 29 + (i / 9) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                @Override
                public boolean allowModification(Player player) {
                    return false;
                }

                @Override
                public ItemStack safeInsert(ItemStack stack, int count) {
                    return stack;
                }

                @Override
                public ItemStack safeTake(int count, int maxCount, Player player) {
                    return ItemStack.EMPTY;
                }
            });
        }
        addSlot(new Slot(workbench == null ? new SimpleContainer(1) : workbench.inventory(), 0, 152, 8) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof EssentiaCellItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 169 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 227));
        }
        refreshFilters();
    }

    public static EssentiaKey aspectFromItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof IAspectItem item) {
            var aspects = item.getAspects(stack);
            if (aspects != null && aspects.size() > 0) {
                Aspect aspect = aspects.getAspects()[0];
                if (aspect != null) return EssentiaKey.of(aspect);
            }
        }
        var inspected = PortableAspectItems.inspect(stack);
        if (inspected.isPresent()) {
            var contents = inspected.get();
            if (contents.aspect() != null) return EssentiaKey.of(contents.aspect());
            if (contents.filter() != null) return EssentiaKey.of(contents.filter());
        }
        return null;
    }

    private List<String> partitions() {
        return getSlot(CELL_SLOT).getItem().getOrDefault(ThaumicEnergistics.CELL_CONTENTS, CellContents.EMPTY).partitions();
    }

    private void refreshFilters() {
        if (workbench == null) return;
        var tags = partitions();
        for (int i = 0; i < FILTER_COUNT; i++) {
            ghosts.setItem(i, i < tags.size()
                    ? GenericStack.wrapInItemStack(EssentiaKey.ofTag(tags.get(i)), 1)
                    : ItemStack.EMPTY);
        }
    }

    public EssentiaKey filter(int index) {
        var generic = GenericStack.unwrapItemStack(ghosts.getItem(index));
        return generic != null && generic.what() instanceof EssentiaKey key ? key : null;
    }

    @Override
    public void broadcastChanges() {
        refreshFilters();
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        if (owner != player) return false;
        if (workbench == null) return player.level().isClientSide;
        var pos = workbench.getBlockPos();
        return player.level() == workbench.getLevel()
                && player.level().hasChunkAt(pos)
                && !workbench.isRemoved()
                && player.level().getBlockEntity(pos) == workbench
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64
                && Platform.hasPermissions(new DimensionalBlockPos(player.level(), pos), player);
    }

    private boolean addAspect(EssentiaKey aspect) {
        var cell = workbench.cell();
        if (cell == null || aspect == null) return false;
        var tags = new ArrayList<>(partitions());
        if (tags.contains(aspect.tag()) || tags.size() >= FILTER_COUNT) return false;
        tags.add(aspect.tag());
        cell.partitions(tags);
        broadcastChanges();
        return true;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index >= CELL_SLOT && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.index >= CELL_SLOT && super.canDragTo(slot);
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (slot >= 0 && slot < FILTER_COUNT) {
            if (workbench != null && type == ClickType.PICKUP && (button == 0 || button == 1)) {
                var cell = workbench.cell();
                if (cell == null) return;
                var tags = new ArrayList<>(partitions());
                var from = slot < tags.size() ? tags.get(slot) : null;
                var to = aspectFromItem(getCarried());
                if (from == null) {
                    if (to != null) addAspect(to);
                } else {
                    // Original replacement preserves duplicate occurrences; removal acts on the first occurrence.
                    int first = tags.indexOf(from);
                    if (to == null) tags.remove(first); else tags.set(first, to.tag());
                    cell.partitions(tags);
                    broadcastChanges();
                }
            }
            return;
        }
        super.clicked(slot, button, type, player);
        broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int action) {
        if (workbench == null || !stillValid(player) || workbench.cell() == null) return false;
        var cell = workbench.cell();
        if (action == CLEAR) {
            cell.partitions(List.of());
        } else if (action == PARTITION_CONTENTS) {
            cell.partitions(List.copyOf(cell.ledger().contents().amounts().keySet()));
        } else {
            return false;
        }
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (workbench == null || !stillValid(player) || index < CELL_SLOT || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        boolean moved;
        if (index == CELL_SLOT) {
            moved = moveItemStackTo(stack, 64, 100, false);
        } else {
            moved = !getSlot(CELL_SLOT).hasItem() && moveItemStackTo(stack, CELL_SLOT, CELL_SLOT + 1, false);
            if (!moved && addAspect(aspectFromItem(stack))) return ItemStack.EMPTY;
            if (!moved) moved = index < 91 ? moveItemStackTo(stack, 91, 100, false) : moveItemStackTo(stack, 64, 91, false);
        }
        if (moved) {
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            broadcastChanges();
        }
        return ItemStack.EMPTY;
    }
}
