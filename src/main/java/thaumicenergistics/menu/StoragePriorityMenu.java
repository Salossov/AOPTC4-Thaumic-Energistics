package thaumicenergistics.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.EssentiaStorageBusPart;

public final class StoragePriorityMenu extends AbstractContainerMenu {
    private static final int[] DELTAS = {1,10,100,-1,-10,-100};
    private final Player owner;
    private final EssentiaStorageBusPart part;
    private final int[] words = new int[2];
    public StoragePriorityMenu(int id, Inventory inventory, EssentiaStorageBusPart part) {
        super(ThaumicEnergistics.PRIORITY_MENU.get(),id);
        owner = inventory.player; this.part = part;
        addDataSlots(new ContainerData() {
            public int get(int index) { return words[index]; }
            public void set(int index, int value) { words[index] = value & 0xffff; }
            public int getCount() { return 2; }
        });
        refresh();
    }
    public int priority() { return (words[1] << 16) | (words[0] & 0xffff); }
    private void refresh() { if (part != null) { words[0] = part.priority() & 0xffff; words[1] = part.priority() >>> 16; } }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) { return owner == player && (part == null ? player.level().isClientSide : StorageBusMenu.partValid(part,player)); }
    public boolean setPriority(Player player, int containerId, int value) {
        if (part == null || player.containerMenu != this || this.containerId != containerId || !stillValid(player)) return false;
        part.priority(value); broadcastChanges(); return true;
    }
    @Override public boolean clickMenuButton(Player player, int action) {
        if (part == null || !stillValid(player)) return false;
        if (action == 0) part.openMenu(player,false);
        else if (action >= 1 && action <= DELTAS.length) {
            // Original ContainerPriority uses signed Java int addition, including wraparound.
            part.priority(part.priority() + DELTAS[action - 1]); broadcastChanges();
        } else return false;
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
