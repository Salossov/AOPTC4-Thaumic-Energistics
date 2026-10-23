package thaumicenergistics.menu;

import appeng.api.stacks.GenericStack;
import appeng.items.contents.NetworkToolMenuHost;
import appeng.items.tools.NetworkToolItem;
import appeng.util.Platform;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.EssentiaStorageBusPart;
import thaumicenergistics.storage.EssentiaKey;

/**
 * Storage Bus Menu.
 * Layout: 9 ghost aspect filter slots, 1 upgrade slot (inverter card), player inventory,
 * and optional network tool storage. Supports safe quickMove without ghost drops, and
 * server-side permission and range checks.
 */
public final class StorageBusMenu extends AbstractContainerMenu {
    public static final int PRIORITY = 0, VOID = 1;

    private final Player owner;
    private final EssentiaStorageBusPart part;
    private final int toolSlot;
    private final ItemStack toolStack;
    private final NetworkToolMenuHost<?> toolHost;
    private final SimpleContainer filters = new SimpleContainer(9);
    private int voidState;

    public static StorageBusMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        return new StorageBusMenu(id, inventory, null, data.readVarInt());
    }

    public StorageBusMenu(int id, Inventory inventory, EssentiaStorageBusPart part) {
        this(id, inventory, part, EssentiaBusMenu.findToolSlot(inventory.player));
    }

    private StorageBusMenu(int id, Inventory inventory, EssentiaStorageBusPart part, int toolSlot) {
        super(ThaumicEnergistics.STORAGE_BUS_MENU.get(), id);
        this.owner = inventory.player;
        this.part = part;
        this.toolSlot = toolSlot;
        this.toolStack = toolSlot < 0 ? ItemStack.EMPTY : inventory.getItem(toolSlot);
        this.toolHost = part != null && toolSlot >= 0 ? NetworkToolItem.findNetworkToolInv(owner) : null;

        // Ghost filter slots: 0 to 8 (3x3 grid at 62, 22)
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(filters, i, 62 + (i % 3) * 18, 22 + (i / 3) * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }

        // Upgrade slot: index 9 (at 187, 8)
        var upgrades = part == null ? null : part.getUpgrades();
        addSlot(new Slot(upgrades == null ? new SimpleContainer(1) : upgrades.toContainer(), 0, 187, 8) {
            @Override public boolean mayPlace(ItemStack stack) { return upgrades == null || upgrades.isItemValid(0, stack); }
            @Override public int getMaxStackSize() { return 1; }
        });

        // Player main inventory (10 to 36)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addPlayerSlot(inventory, 9 + row * 9 + col, 8 + col * 18, 102 + row * 18);
            }
        }

        // Hotbar (37 to 45)
        for (int col = 0; col < 9; col++) {
            addPlayerSlot(inventory, col, 8 + col * 18, 160);
        }

        // Network tool (46 to 54) if present
        if (toolSlot >= 0) {
            var tool = toolHost == null ? null : toolHost.getInventory();
            var container = tool == null ? new SimpleContainer(9) : tool.toContainer();
            // Original ContainerWithNetworkTool binds columns before rows
            for (int col = 0; col < 3; col++) {
                for (int row = 0; row < 3; row++) {
                    final int toolIndex = col + row * 3;
                    addSlot(new Slot(container, toolIndex, 187 + col * 18, 102 + row * 18) {
                        @Override public boolean mayPlace(ItemStack stack) { return tool == null || tool.isItemValid(toolIndex, stack); }
                    });
                }
            }
        }

        addDataSlots(new ContainerData() {
            @Override public int get(int index) { return voidState; }
            @Override public void set(int index, int value) { voidState = value; }
            @Override public int getCount() { return 1; }
        });

        refreshState();
    }

    private void addPlayerSlot(Inventory inventory, int playerSlot, int x, int y) {
        addSlot(new Slot(inventory, playerSlot, x, y) {
            @Override public boolean mayPickup(Player player) { return playerSlot != toolSlot; }
            @Override public boolean mayPlace(ItemStack stack) { return playerSlot != toolSlot; }
        });
    }

    public boolean hasNetworkTool() {
        return toolSlot >= 0;
    }

    public boolean allowVoid() {
        return voidState != 0;
    }

    public EssentiaKey filter(int index) {
        var stack = GenericStack.unwrapItemStack(filters.getItem(index));
        return stack != null && stack.what() instanceof EssentiaKey key ? key : null;
    }

    private void refreshState() {
        if (part == null) return;
        voidState = part.allowVoid() ? 1 : 0;
        for (int i = 0; i < 9; i++) {
            filters.setItem(i, part.filter(i).isEmpty() ? ItemStack.EMPTY : GenericStack.wrapInItemStack(EssentiaKey.ofTag(part.filter(i)), 1));
        }
    }

    @Override
    public void broadcastChanges() {
        refreshState();
        super.broadcastChanges();
    }

    public static boolean partValid(EssentiaStorageBusPart part, Player player) {
        if (part == null || part.getHost() == null) return false;
        var tile = part.getBlockEntity();
        if (tile == null || tile.isRemoved()) return false;
        var pos = tile.getBlockPos();
        return player.level() == part.getLevel()
                && player.level().hasChunkAt(pos)
                && player.level().getBlockEntity(pos) == tile
                && part.getHost().getPart(part.getSide()) == part
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64
                && Platform.hasPermissions(part.getHost().getLocation(), player);
    }

    @Override
    public boolean stillValid(Player player) {
        if (player != owner) return false;
        if (part == null) return player.level().isClientSide;
        return partValid(part, player) && (toolSlot < 0 || (owner.getInventory().getItem(toolSlot) == toolStack && toolHost != null && toolHost.isValid()));
    }

    private boolean setFilter(int index, ItemStack stack) {
        var key = EssentiaBusMenu.aspectFromItem(stack);
        if (key != null) {
            for (int i = 0; i < 9; i++) {
                if (key.tag().equals(part.filter(i))) return false;
            }
        }
        // Original GUI clears a filter when the cursor has no readable aspect, including an ordinary item
        part.filter(index, key);
        broadcastChanges();
        return true;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || (type == ClickType.SWAP && button == toolSlot)) return;
        if (slot >= 0 && slot < 9) {
            if (part != null && type == ClickType.PICKUP && (button == 0 || button == 1)) {
                setFilter(slot, getCarried());
            }
            return;
        }
        super.clicked(slot, button, type, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int action) {
        if (part == null || !stillValid(player)) return false;
        if (action == VOID) {
            part.allowVoid(!part.allowVoid());
            broadcastChanges();
        } else if (action == PRIORITY) {
            part.openMenu(player, true);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (part == null || !stillValid(player) || index < 9 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var key = EssentiaBusMenu.aspectFromItem(stack);
        if (key != null) {
            for (int i = 0; i < 9; i++) {
                if (key.tag().equals(part.filter(i))) return ItemStack.EMPTY;
            }
            for (int i = 0; i < 9; i++) {
                if (part.filter(i).isEmpty() && setFilter(i, stack)) return ItemStack.EMPTY;
            }
        }

        boolean moved = false;
        var upgradeSlot = slots.get(9);

        if (index >= 10 && index < 46) {
            // Player inventory -> Upgrade slot -> Network tool -> Player hotbar/main
            if (upgradeSlot.mayPlace(stack)) {
                moved = moveItemStackTo(stack, 9, 10, false);
            }
            if (!moved && hasNetworkTool()) {
                for (int t = 46; t < 55 && !stack.isEmpty(); t++) {
                    if (slots.get(t).mayPlace(stack)) {
                        moved |= moveItemStackTo(stack, t, t + 1, false);
                    }
                }
            }
            if (!moved) {
                moved = index < 37 ? moveItemStackTo(stack, 37, 46, false) : moveItemStackTo(stack, 10, 37, false);
            }
        } else if (index == 9) {
            // Upgrade slot -> Network tool -> Player inventory
            if (hasNetworkTool()) {
                for (int t = 46; t < 55 && !stack.isEmpty(); t++) {
                    if (slots.get(t).mayPlace(stack)) {
                        moved |= moveItemStackTo(stack, t, t + 1, false);
                    }
                }
            }
            if (!moved) {
                moved = moveItemStackTo(stack, 10, 46, false);
            }
        } else if (index >= 46 && index < 55) {
            // Network tool -> Upgrade slot -> Player inventory
            if (upgradeSlot.mayPlace(stack)) {
                moved = moveItemStackTo(stack, 9, 10, false);
            }
            if (!moved) {
                moved = moveItemStackTo(stack, 10, 46, false);
            }
        }

        if (moved) {
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            broadcastChanges();
        }
        return ItemStack.EMPTY;
    }
}
