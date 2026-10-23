package thaumicenergistics.distillation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for Distillation Pattern Encoder.
 * Device does not tick, does not connect to ME grid, and does not draw power.
 * Contains 3 persistent slots:
 * - 0: Ghost source item (never dropped)
 * - 1: Blank patterns (real item)
 * - 2: Encoded pattern (real item)
 * External automation (hoppers/pipes) is blocked.
 */
public final class DistillationPatternEncoderBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_SOURCE_ITEM = 0;
    public static final int SLOT_BLANK_PATTERNS = 1;
    public static final int SLOT_ENCODED_PATTERN = 2;
    public static final int SLOT_TOTAL_COUNT = 3;

    private final SimpleContainer inventory = new SimpleContainer(SLOT_TOTAL_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == SLOT_SOURCE_ITEM) return true;
            if (slot == SLOT_BLANK_PATTERNS) return DistillationPatternHelper.isBlankPattern(stack);
            if (slot == SLOT_ENCODED_PATTERN) return DistillationPatternHelper.isValidPattern(stack);
            return false;
        }

        @Override
        public int getMaxStackSize(int slot) {
            return slot == SLOT_ENCODED_PATTERN ? 1 : 64;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            DistillationPatternEncoderBlockEntity.this.setChanged();
        }
    };

    public DistillationPatternEncoderBlockEntity(BlockPos pos, BlockState state) {
        super(DistillationRegistrar.BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public void dropInventory(Level level, BlockPos pos) {
        // Drop ONLY real pattern items from slot 1 and 2. Ghost source (slot 0) is never dropped!
        var blanks = inventory.removeItemNoUpdate(SLOT_BLANK_PATTERNS);
        if (!blanks.isEmpty()) {
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, blanks));
        }
        var encoded = inventory.removeItemNoUpdate(SLOT_ENCODED_PATTERN);
        if (!encoded.isEmpty()) {
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, encoded));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        if (!inventory.getItem(SLOT_SOURCE_ITEM).isEmpty()) {
            data.put("SourceItem", inventory.getItem(SLOT_SOURCE_ITEM).save(registries));
        }
        if (!inventory.getItem(SLOT_BLANK_PATTERNS).isEmpty()) {
            data.put("BlankPatterns", inventory.getItem(SLOT_BLANK_PATTERNS).save(registries));
        }
        if (!inventory.getItem(SLOT_ENCODED_PATTERN).isEmpty()) {
            data.put("EncodedPattern", inventory.getItem(SLOT_ENCODED_PATTERN).save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.loadAdditional(data, registries);
        if (data.contains("SourceItem", Tag.TAG_COMPOUND)) {
            inventory.setItem(SLOT_SOURCE_ITEM, ItemStack.parseOptional(registries, data.getCompound("SourceItem")));
        }
        if (data.contains("BlankPatterns", Tag.TAG_COMPOUND)) {
            inventory.setItem(SLOT_BLANK_PATTERNS, ItemStack.parseOptional(registries, data.getCompound("BlankPatterns")));
        }
        if (data.contains("EncodedPattern", Tag.TAG_COMPOUND)) {
            inventory.setItem(SLOT_ENCODED_PATTERN, ItemStack.parseOptional(registries, data.getCompound("EncodedPattern")));
        }
        // Backward-compatibility: 1.7.10 "inventory" TagList
        if (data.contains("inventory", Tag.TAG_LIST)) {
            ListTag list = data.getList("inventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag slotTag = list.getCompound(i);
                int slot = slotTag.getByte("Slot") & 255;
                if (slot >= 0 && slot < SLOT_TOTAL_COUNT) {
                    inventory.setItem(slot, ItemStack.parseOptional(registries, slotTag));
                }
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("thaumicenergistics.block.distillation.encoder.name");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new DistillationPatternEncoderMenu(id, playerInventory, this);
    }
}
