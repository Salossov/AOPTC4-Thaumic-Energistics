package thaumicenergistics.knowledge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Encodes arcane recipes into a Knowledge Core.
 * Inventory with 1 slot (KCORE_SLOT = 0), max stack 1, accepts only ItemKnowledgeCore.
 * Does not connect to ME grid, does not consume power, does not tick.
 */
public class KnowledgeInscriberBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int KCORE_SLOT = 0;
    public static final int INVENTORY_SIZE = 1;
    private static final String NBTKEY_KCORE = "kcore";

    private ItemStack coreStack = ItemStack.EMPTY;

    public KnowledgeInscriberBlockEntity(BlockPos pos, BlockState state) {
        super(KnowledgeRegistrar.KNOWLEDGE_INSCRIBER_BE.get(), pos, state);
    }

    public boolean hasKCore() {
        return !coreStack.isEmpty() && coreStack.getItem() instanceof ItemKnowledgeCore;
    }

    public ItemStack getKCore() {
        return coreStack;
    }

    @Override
    public int getContainerSize() {
        return INVENTORY_SIZE;
    }

    @Override
    public boolean isEmpty() {
        return coreStack.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == KCORE_SLOT ? coreStack : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == KCORE_SLOT && !coreStack.isEmpty() && amount > 0) {
            ItemStack removed = coreStack.split(amount);
            if (coreStack.isEmpty()) {
                coreStack = ItemStack.EMPTY;
            }
            setChanged();
            return removed;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot == KCORE_SLOT && !coreStack.isEmpty()) {
            ItemStack existing = coreStack;
            coreStack = ItemStack.EMPTY;
            return existing;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == KCORE_SLOT) {
            if (stack.isEmpty() || stack.getItem() instanceof ItemKnowledgeCore) {
                this.coreStack = stack;
                if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
                    stack.setCount(getMaxStackSize());
                }
                setChanged();
            }
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null || this.isRemoved() || player.level() != this.level ||
                !this.level.hasChunkAt(this.worldPosition) || this.level.getBlockEntity(this.worldPosition) != this ||
                !player.isAlive() || player instanceof net.neoforged.neoforge.common.util.FakePlayer ||
                !appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(this.level, this.worldPosition), player)) {
            return false;
        }
        return player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == KCORE_SLOT && !stack.isEmpty() && stack.getItem() instanceof ItemKnowledgeCore;
    }

    @Override
    public void clearContent() {
        this.coreStack = ItemStack.EMPTY;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.thaumicenergistics.knowledge_inscriber");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new KnowledgeInscriberMenu(containerId, playerInventory, this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(NBTKEY_KCORE, Tag.TAG_COMPOUND)) {
            var opt = ItemStack.parse(registries, tag.getCompound(NBTKEY_KCORE));
            this.coreStack = opt.orElse(ItemStack.EMPTY);
        } else {
            this.coreStack = ItemStack.EMPTY;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!coreStack.isEmpty()) {
            tag.put(NBTKEY_KCORE, coreStack.save(registries));
        }
    }
}
