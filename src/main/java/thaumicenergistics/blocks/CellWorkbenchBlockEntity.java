package thaumicenergistics.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.CellWorkbenchMenu;
import thaumicenergistics.storage.EssentiaCell;
import thaumicenergistics.storage.EssentiaCellItem;

/** One persistent cell, no ticking or hidden copy of its contents. */
public final class CellWorkbenchBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer inventory = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && stack.getItem() instanceof EssentiaCellItem; }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() { super.setChanged(); CellWorkbenchBlockEntity.this.setChanged(); }
    };
    public CellWorkbenchBlockEntity(BlockPos pos, BlockState state) { super(ThaumicEnergistics.CELL_WORKBENCH_ENTITY.get(), pos, state); }
    public SimpleContainer inventory() { return inventory; }
    public EssentiaCell cell() {
        var stack = inventory.getItem(0);
        return stack.getItem() instanceof EssentiaCellItem ? new EssentiaCell(stack, this::setChanged) : null;
    }
    @Override protected void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        if (!inventory.getItem(0).isEmpty()) data.put("EssentiaCell", inventory.getItem(0).save(registries));
    }
    @Override protected void loadAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.loadAdditional(data, registries);
        var stack = ItemStack.parseOptional(registries, data.getCompound("EssentiaCell"));
        inventory.setItem(0, stack.getItem() instanceof EssentiaCellItem ? stack : ItemStack.EMPTY);
    }
    @Override public Component getDisplayName() { return Component.translatable("thaumicenergistics.gui.essentia.cell.workbench.title"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new CellWorkbenchMenu(id, inventory, this); }
}
