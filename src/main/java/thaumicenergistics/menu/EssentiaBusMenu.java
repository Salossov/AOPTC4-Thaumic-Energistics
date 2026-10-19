package thaumicenergistics.menu;

import appeng.api.config.RedstoneMode;
import appeng.api.inventories.InternalInventory;
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
import thaumcraft.api.aspects.IAspectItem;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.EssentiaIOBusPart;
import thaumicenergistics.storage.EssentiaKey;

/** Original nine ghost filters, four upgrades and optional network-tool inventory. */
public final class EssentiaBusMenu extends AbstractContainerMenu {
    public static final int REDSTONE_BUTTON = 9, VOID_BUTTON = 10;
    private final Player owner;
    private final EssentiaIOBusPart part;
    private final boolean importing;
    private final int toolSlot;
    private final ItemStack toolStack;
    private final NetworkToolMenuHost<?> toolHost;
    private final SimpleContainer filters = new SimpleContainer(9);
    private final int[] state = new int[4];

    public static EssentiaBusMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        return new EssentiaBusMenu(id, inventory, null, data.readBoolean(), data.readVarInt());
    }

    public EssentiaBusMenu(int id, Inventory inventory, EssentiaIOBusPart part) {
        this(id, inventory, part, part.importing(), findToolSlot(inventory.player));
    }

    public static int findToolSlot(Player player) {
        var tool = NetworkToolItem.findNetworkToolInv(player);
        return tool == null || tool.getPlayerInventorySlot() == null ? -1 : tool.getPlayerInventorySlot();
    }

    private EssentiaBusMenu(int id, Inventory inventory, EssentiaIOBusPart part, boolean importing, int toolSlot) {
        super(ThaumicEnergistics.BUS_MENU.get(), id);
        this.owner = inventory.player;
        this.part = part;
        this.importing = importing;
        this.toolSlot = toolSlot;
        toolStack = toolSlot >= 0 ? inventory.getItem(toolSlot) : ItemStack.EMPTY;
        toolHost = part != null && toolSlot >= 0 ? NetworkToolItem.findNetworkToolInv(owner) : null;
        for (int i = 0; i < 9; i++) {
            final int filterIndex = i;
            addSlot(new Slot(filters, i, 62 + (i % 3) * 18, 22 + (i / 3) * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public boolean isActive() { return filterEnabled(filterIndex); }
            });
        }
        var upgrades = part == null ? null : part.getUpgrades();
        var upgradeContainer = upgrades == null ? new SimpleContainer(4) : upgrades.toContainer();
        for (int i = 0; i < 4; i++) {
            final int upgradeIndex = i;
            addSlot(new Slot(upgradeContainer, i, 187, 8 + i * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return upgrades == null || upgrades.isItemValid(upgradeIndex, stack); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addPlayerSlot(inventory, col + (row + 1) * 9, 8 + col * 18, 102 + row * 18);
        for (int col = 0; col < 9; col++) addPlayerSlot(inventory, col, 8 + col * 18, 160);
        if (toolSlot >= 0) {
            InternalInventory tool = part == null ? null : toolHost.getInventory();
            var container = tool == null ? new SimpleContainer(9) : tool.toContainer();
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                final int toolIndex = row * 3 + col;
                addSlot(new Slot(container, toolIndex, 187 + col * 18, 102 + row * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return tool == null || tool.isItemValid(toolIndex, stack); }
                });
            }
        }
        addDataSlots(new ContainerData() {
            public int get(int index) { return state[index]; }
            public void set(int index, int value) { state[index] = value; }
            public int getCount() { return state.length; }
        });
        refreshState();
    }

    private void addPlayerSlot(Inventory inventory, int playerSlot, int x, int y) {
        addSlot(new Slot(inventory, playerSlot, x, y) {
            @Override public boolean mayPickup(Player player) { return playerSlot != toolSlot; }
            @Override public boolean mayPlace(ItemStack stack) { return playerSlot != toolSlot; }
        });
    }

    public boolean importing() { return importing; }
    public boolean hasNetworkTool() { return toolSlot >= 0; }
    public boolean hasRedstoneCard() { return state[3] != 0; }
    public boolean allowVoid() { return state[2] != 0; }
    public RedstoneMode redstoneMode() { return RedstoneMode.values()[Math.clamp(state[1], 0, RedstoneMode.values().length - 1)]; }
    public boolean filterEnabled(int slot) {
        int[] required = {2,1,2,1,0,1,2,1,2};
        return slot >= 0 && slot < 9 && state[0] >= required[slot];
    }
    public EssentiaKey filter(int slot) {
        var generic = GenericStack.unwrapItemStack(filters.getItem(slot));
        return generic != null && generic.what() instanceof EssentiaKey key ? key : null;
    }

    private void refreshState() {
        if (part == null) return;
        state[0] = part.capacityCards();
        state[1] = part.redstoneMode().ordinal();
        state[2] = part.allowVoid() ? 1 : 0;
        state[3] = part.hasRedstoneCard() ? 1 : 0;
        for (int i = 0; i < 9; i++) {
            String tag = part.filter(i);
            filters.setItem(i, tag.isEmpty() ? ItemStack.EMPTY : GenericStack.wrapInItemStack(EssentiaKey.ofTag(tag), 1));
        }
    }

    @Override public void broadcastChanges() { refreshState(); super.broadcastChanges(); }

    @Override public boolean stillValid(Player player) {
        if (player != owner) return false;
        if (part == null) return player.level().isClientSide;
        var level = part.getLevel();
        var pos = part.getBlockEntity().getBlockPos();
        return player.level() == level && level.hasChunkAt(pos) && !part.getBlockEntity().isRemoved()
                && level.getBlockEntity(pos) == part.getBlockEntity() && part.getHost().getPart(part.getSide()) == part
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64
                && Platform.hasPermissions(part.getHost().getLocation(), player)
                && (toolSlot < 0 || (owner.getInventory().getItem(toolSlot) == toolStack && toolHost.isValid()));
    }

    public static EssentiaKey aspectFromItem(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IAspectItem item)) return null;
        var aspects = item.getAspects(stack);
        return aspects == null || aspects.size() == 0 ? null : EssentiaKey.of(aspects.getAspects()[0]);
    }

    private boolean setFilter(int slot, ItemStack stack) {
        if (!filterEnabled(slot)) return false;
        var key = aspectFromItem(stack);
        if (key == null && !stack.isEmpty()) return false;
        if (key != null) for (int i = 0; i < 9; i++) if (key.tag().equals(part.filter(i))) return false;
        part.filter(slot, key);
        broadcastChanges();
        return true;
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (type == ClickType.SWAP && button == toolSlot) return;
        if (slot >= 0 && slot < 9) {
            if (part != null && type == ClickType.PICKUP && (button == 0 || button == 1)) setFilter(slot, getCarried());
            return;
        }
        super.clicked(slot, button, type, player);
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (part == null || !stillValid(player)) return false;
        if (button == REDSTONE_BUTTON && part.hasRedstoneCard()) {
            var modes = RedstoneMode.values();
            part.redstoneMode(modes[(part.redstoneMode().ordinal() + 1) % modes.length]);
        } else if (button == VOID_BUTTON && !importing) part.allowVoid(!part.allowVoid());
        else return false;
        broadcastChanges();
        return true;
    }

    private boolean moveToUpgrades(ItemStack stack) {
        boolean moved = false;
        // Vanilla stops after the first empty slot; the original fills all four.
        for (int i = 9; i < 13 && !stack.isEmpty(); i++) moved |= moveItemStackTo(stack, i, i + 1, false);
        return moved;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (part == null || !stillValid(player) || index < 9 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var aspect = aspectFromItem(stack);
        if (aspect != null) {
            for (int i = 0; i < 9; i++) if (aspect.tag().equals(part.filter(i))) return ItemStack.EMPTY;
            for (int i : part.availableFilterSlots())
                if (part.filter(i).isEmpty() && setFilter(i, stack)) return ItemStack.EMPTY;
        }
        boolean moved;
        if (index >= 13 && index < 49) {
            moved = moveToUpgrades(stack);
            if (!moved && hasNetworkTool()) moved = moveItemStackTo(stack, 49, 58, false);
            if (!moved) moved = index < 40 ? moveItemStackTo(stack, 40, 49, false) : moveItemStackTo(stack, 13, 40, false);
        } else if (index < 13) {
            moved = hasNetworkTool() && moveItemStackTo(stack, 49, 58, false);
            if (!moved) moved = moveItemStackTo(stack, 13, 49, true);
        } else {
            moved = moveToUpgrades(stack);
            if (!moved) moved = moveItemStackTo(stack, 13, 49, true);
        }
        if (moved) { if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); broadcastChanges(); }
        // Prevent vanilla's repeated shift-click retry, as in the original container.
        return ItemStack.EMPTY;
    }
}
