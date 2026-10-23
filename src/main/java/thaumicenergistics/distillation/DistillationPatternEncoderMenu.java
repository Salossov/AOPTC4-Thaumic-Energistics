package thaumicenergistics.distillation;

import appeng.api.stacks.GenericStack;
import appeng.api.util.DimensionalBlockPos;
import appeng.util.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectApi;
import thaumcraft.api.research.ResearchApi;
import thaumicenergistics.storage.EssentiaKey;

/**
 * Menu for Distillation Pattern Encoder.
 * - 0: Ghost source item (never dropped)
 * - 1: Blank patterns (real items)
 * - 2: Encoded pattern (real item)
 * - 3..8: 6 source aspect widgets (read-only, click to select)
 * - 9: Selected aspect widget (read-only display, authoritatively synced)
 * - 10..36: Player main inventory
 * - 37..45: Player hotbar
 */
public final class DistillationPatternEncoderMenu extends AbstractContainerMenu {
    public static final int SLOT_SOURCE_ITEM = 0;
    public static final int SLOT_BLANK_PATTERNS = 1;
    public static final int SLOT_ENCODED_PATTERN = 2;
    public static final int SLOT_SOURCE_ASPECTS_START = 3;
    public static final int SLOT_SOURCE_ASPECTS_COUNT = 6;
    public static final int SLOT_SELECTED_ASPECT = 9;
    public static final int PLAYER_INV_START = 10;
    public static final int PLAYER_INV_COUNT = 27;
    public static final int HOTBAR_START = 37;
    public static final int HOTBAR_COUNT = 9;
    public static final int TOTAL_SLOTS = 46;

    private final Player owner;
    private final DistillationPatternEncoderBlockEntity blockEntity;
    private final Container aspectContainer = new SimpleContainer(7); // 0..5 source aspects, 6 selected aspect

    private ItemStack cachedSource = ItemStack.EMPTY;
    private ItemStack cachedPattern = ItemStack.EMPTY;
    private Aspect selectedAspect = null;
    private int selectedAmount = 0;

    public DistillationPatternEncoderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory, getBlockEntityFromBuf(playerInventory.player, data));
    }

    private static DistillationPatternEncoderBlockEntity getBlockEntityFromBuf(Player player, FriendlyByteBuf data) {
        if (player != null && player.level() != null && data != null && data.readableBytes() >= 8) {
            BlockPos pos = data.readBlockPos();
            if (player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) instanceof DistillationPatternEncoderBlockEntity be) {
                return be;
            }
        }
        return null;
    }

    public DistillationPatternEncoderMenu(int containerId, Inventory playerInventory, DistillationPatternEncoderBlockEntity blockEntity) {
        super(DistillationRegistrar.MENU_TYPE.get(), containerId);
        this.owner = playerInventory.player;
        this.blockEntity = blockEntity;

        Container inv = blockEntity != null ? blockEntity.inventory() : new SimpleContainer(3);

        // Slot 0: Ghost source item at (15, 69)
        addSlot(new Slot(inv, DistillationPatternEncoderBlockEntity.SLOT_SOURCE_ITEM, 15, 69) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });

        // Slot 1: Blank patterns at (146, 75)
        addSlot(new Slot(inv, DistillationPatternEncoderBlockEntity.SLOT_BLANK_PATTERNS, 146, 75) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return DistillationPatternHelper.isBlankPattern(stack);
            }
        });

        // Slot 2: Encoded pattern at (146, 113)
        addSlot(new Slot(inv, DistillationPatternEncoderBlockEntity.SLOT_ENCODED_PATTERN, 146, 113) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return DistillationPatternHelper.isValidPattern(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Slots 3..8: 6 Source aspects at (65, 24 + i * 18)
        for (int i = 0; i < SLOT_SOURCE_ASPECTS_COUNT; i++) {
            addSlot(new Slot(aspectContainer, i, 65, 24 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }

        // Slot 9: Selected aspect at (116, 69)
        addSlot(new Slot(aspectContainer, 6, 116, 69) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });

        // Player inventory (10..36) at (8, 152)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 152 + row * 18));
            }
        }

        // Hotbar (37..45) at (8, 210)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 210));
        }

        if (blockEntity != null && !owner.level().isClientSide) {
            cachedSource = getSlot(SLOT_SOURCE_ITEM).getItem().copy();
            cachedPattern = getSlot(SLOT_ENCODED_PATTERN).getItem().copy();
            if (!cachedPattern.isEmpty()) {
                loadPattern(cachedPattern);
            } else if (!cachedSource.isEmpty()) {
                scanSourceItem(true);
            }
        }
    }

    public DistillationPatternEncoderBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public Player getPlayer() {
        return owner;
    }

    public Aspect getSelectedAspect() {
        ItemStack slotStack = getSlot(SLOT_SELECTED_ASPECT).getItem();
        if (!slotStack.isEmpty()) {
            var generic = GenericStack.unwrapItemStack(slotStack);
            if (generic != null && generic.what() instanceof EssentiaKey essentiaKey) {
                return essentiaKey.aspect();
            }
        }
        return null;
    }

    public int getSelectedAmount() {
        ItemStack slotStack = getSlot(SLOT_SELECTED_ASPECT).getItem();
        if (!slotStack.isEmpty()) {
            var generic = GenericStack.unwrapItemStack(slotStack);
            if (generic != null && generic.what() instanceof EssentiaKey) {
                return (int) generic.amount();
            }
        }
        return 0;
    }

    public static boolean isScanned(Player player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (player instanceof ServerPlayer sp) {
            return AspectApi.scanned(sp, stack);
        } else {
            return AspectApi.scanned(stack);
        }
    }

    public static boolean knowsAspect(Player player, Aspect aspect) {
        if (aspect == null) return false;
        if (player instanceof ServerPlayer sp) {
            return ResearchApi.knowledge(sp).knows(aspect);
        } else {
            return ResearchApi.clientKnowledge().knows(aspect);
        }
    }

    public void clearAspectSlots() {
        for (int i = 0; i < 7; i++) {
            aspectContainer.setItem(i, ItemStack.EMPTY);
        }
    }

    public void clearSelectedAspect() {
        selectedAspect = null;
        selectedAmount = 0;
        aspectContainer.setItem(6, ItemStack.EMPTY);
    }

    public void setSelectedAspect(Aspect aspect, int amount) {
        this.selectedAspect = aspect;
        this.selectedAmount = amount;
        if (aspect != null && amount > 0) {
            aspectContainer.setItem(6, GenericStack.wrapInItemStack(EssentiaKey.of(aspect), amount));
        } else {
            aspectContainer.setItem(6, ItemStack.EMPTY);
        }
    }

    public void scanSourceItem(boolean autoSelectFirst) {
        if (owner.level().isClientSide) return;
        clearAspectSlots();
        if (autoSelectFirst) clearSelectedAspect();
        ItemStack source = getSlot(SLOT_SOURCE_ITEM).getItem();
        if (source.isEmpty()) {
            clearSelectedAspect();
            return;
        }

        if (!isScanned(owner, source)) {
            // Unscanned item: mask all 6 slots with unknown aspect placeholder
            for (int i = 0; i < SLOT_SOURCE_ASPECTS_COUNT; i++) {
                aspectContainer.setItem(i, GenericStack.wrapInItemStack(EssentiaKey.ofTag("unknown"), 1));
            }
            clearSelectedAspect();
            return;
        }

        Map<String, Integer> rawAspects = AspectApi.aspects(owner.level(), source);
        if (rawAspects.isEmpty()) {
            clearSelectedAspect();
            return;
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(rawAspects.entrySet());
        sorted.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
        });

        int count = Math.min(SLOT_SOURCE_ASPECTS_COUNT, sorted.size());
        Aspect firstKnown = null;
        int firstAmount = 0;
        boolean currentSelectionStillValid = false;

        for (int i = 0; i < count; i++) {
            var entry = sorted.get(i);
            Aspect aspect = Aspect.getAspect(entry.getKey());
            int amount = entry.getValue();
            if (aspect != null && knowsAspect(owner, aspect)) {
                aspectContainer.setItem(i, GenericStack.wrapInItemStack(EssentiaKey.of(aspect), amount));
                if (firstKnown == null) {
                    firstKnown = aspect;
                    firstAmount = amount;
                }
                if (selectedAspect != null && selectedAspect.equals(aspect)) {
                    currentSelectionStillValid = true;
                    setSelectedAspect(aspect, amount);
                }
            } else {
                aspectContainer.setItem(i, GenericStack.wrapInItemStack(EssentiaKey.ofTag("unknown"), 1));
            }
        }
        for (int i = count; i < SLOT_SOURCE_ASPECTS_COUNT; i++) {
            aspectContainer.setItem(i, ItemStack.EMPTY);
        }

        if (autoSelectFirst && firstKnown != null && selectedAspect == null) {
            setSelectedAspect(firstKnown, firstAmount);
        } else if (!currentSelectionStillValid) {
            clearSelectedAspect();
        }
    }

    public void loadPattern(ItemStack patternStack) {
        if (owner.level().isClientSide) return;
        var decoded = DistillationPatternHelper.decodePattern(patternStack, owner.level());
        if (decoded.valid()) {
            getSlot(SLOT_SOURCE_ITEM).set(decoded.sourceItem().copyWithCount(1));
            cachedSource = decoded.sourceItem().copyWithCount(1);
            scanSourceItem(false);
            if (isScanned(owner, decoded.sourceItem()) && knowsAspect(owner, decoded.aspect())
                    && AspectApi.aspects(owner.level(), decoded.sourceItem()).getOrDefault(decoded.aspect().getTag(), 0) == decoded.amount()) {
                setSelectedAspect(decoded.aspect(), decoded.amount());
            } else {
                clearSelectedAspect();
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (owner.level().isClientSide) {
            super.broadcastChanges();
            return;
        }
        ItemStack currentPattern = getSlot(SLOT_ENCODED_PATTERN).getItem();
        if (!ItemStack.matches(currentPattern, cachedPattern)) {
            cachedPattern = currentPattern.copy();
            if (!currentPattern.isEmpty()) {
                loadPattern(currentPattern);
            }
        }

        ItemStack currentSource = getSlot(SLOT_SOURCE_ITEM).getItem();
        if (!ItemStack.matches(currentSource, cachedSource)) {
            cachedSource = currentSource.copy();
            scanSourceItem(true);
        }

        super.broadcastChanges();
    }

    public boolean canEncode() {
        ItemStack source = getSlot(SLOT_SOURCE_ITEM).getItem();
        if (source.isEmpty() || !isScanned(owner, source)) return false;
        Aspect aspect = getSelectedAspect();
        int amount = getSelectedAmount();
        if (aspect == null || amount <= 0 || !knowsAspect(owner, aspect)) return false;

        Map<String, Integer> rawAspects = AspectApi.aspects(owner.level(), source);
        Integer sourceAmount = rawAspects.get(aspect.getTag());
        if (sourceAmount == null || sourceAmount != amount) return false;

        ItemStack encoded = getSlot(SLOT_ENCODED_PATTERN).getItem();
        ItemStack blank = getSlot(SLOT_BLANK_PATTERNS).getItem();
        return !encoded.isEmpty() || (!blank.isEmpty() && DistillationPatternHelper.isBlankPattern(blank));
    }

    public void onEncodePattern() {
        if (!(owner instanceof ServerPlayer) || !stillValid(owner)) return;
        ItemStack source = getSlot(SLOT_SOURCE_ITEM).getItem();
        if (source.isEmpty() || !isScanned(owner, source)) return;
        Aspect aspect = getSelectedAspect();
        int amount = getSelectedAmount();
        if (aspect == null || amount <= 0 || !knowsAspect(owner, aspect)) return;

        Map<String, Integer> rawAspects = AspectApi.aspects(owner.level(), source);
        Integer sourceAmount = rawAspects.get(aspect.getTag());
        if (sourceAmount == null || sourceAmount != amount) {
            clearSelectedAspect();
            broadcastChanges();
            return;
        }

        ItemStack encodedSlot = getSlot(SLOT_ENCODED_PATTERN).getItem();
        ItemStack blankSlot = getSlot(SLOT_BLANK_PATTERNS).getItem();

        ItemStack newPattern = DistillationPatternHelper.encodePattern(source, aspect, amount);
        if (newPattern.isEmpty()) return;

        if (!encodedSlot.isEmpty()) {
            // Re-encode existing pattern, 0 blanks consumed
            getSlot(SLOT_ENCODED_PATTERN).set(newPattern);
            cachedPattern = newPattern.copy();
        } else if (!blankSlot.isEmpty() && DistillationPatternHelper.isBlankPattern(blankSlot)) {
            // Consume 1 blank pattern and create encoded pattern
            getSlot(SLOT_ENCODED_PATTERN).set(newPattern);
            getSlot(SLOT_BLANK_PATTERNS).remove(1);
            cachedPattern = newPattern.copy();
        }
        broadcastChanges();
    }

    public void onSelectAspectAction(int index) {
        if (!(owner instanceof ServerPlayer) || !stillValid(owner)) return;
        if (index >= 0 && index < SLOT_SOURCE_ASPECTS_COUNT) {
            ItemStack stack = aspectContainer.getItem(index);
            if (!stack.isEmpty()) {
                var generic = GenericStack.unwrapItemStack(stack);
                if (generic != null && generic.what() instanceof EssentiaKey essentiaKey) {
                    Aspect aspect = essentiaKey.aspect();
                    int amount = (int) generic.amount();
                    if (aspect != null && !aspect.getTag().equals("unknown") && knowsAspect(owner, aspect)) {
                        setSelectedAspect(aspect, amount);
                        broadcastChanges();
                    }
                }
            }
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!stillValid(player) || player instanceof FakePlayer) {
            return;
        }

        if (slotId < 0 || slotId >= slots.size()) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        // Guard against any throw / swap / clone / quick-move / drag on ghost and display slots
        if (slotId == SLOT_SOURCE_ITEM || (slotId >= SLOT_SOURCE_ASPECTS_START && slotId <= SLOT_SELECTED_ASPECT)) {
            if (clickType == ClickType.THROW || clickType == ClickType.SWAP || clickType == ClickType.CLONE
                    || clickType == ClickType.QUICK_MOVE || clickType == ClickType.QUICK_CRAFT || clickType == ClickType.PICKUP_ALL) {
                return;
            }
        }

        // Ghost source slot click handling (Slot 0)
        if (slotId == SLOT_SOURCE_ITEM) {
            if (clickType == ClickType.PICKUP && !owner.level().isClientSide) {
                ItemStack carried = getCarried();
                if (carried.isEmpty()) {
                    getSlot(SLOT_SOURCE_ITEM).set(ItemStack.EMPTY);
                } else {
                    getSlot(SLOT_SOURCE_ITEM).set(carried.copyWithCount(1));
                }
                cachedSource = getSlot(SLOT_SOURCE_ITEM).getItem().copy();
                scanSourceItem(true);
                broadcastChanges();
            }
            return;
        }

        // Source aspect widget clicks: select the aspect (Slots 3..8)
        if (slotId >= SLOT_SOURCE_ASPECTS_START && slotId < SLOT_SOURCE_ASPECTS_START + SLOT_SOURCE_ASPECTS_COUNT) {
            if (clickType == ClickType.PICKUP) {
                int aspectIndex = slotId - SLOT_SOURCE_ASPECTS_START;
                onSelectAspectAction(aspectIndex);
            }
            return;
        }

        // Selected aspect slot is purely display (Slot 9)
        if (slotId == SLOT_SELECTED_ASPECT) {
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (!stillValid(player) || slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;
        if (slotIndex == SLOT_SOURCE_ITEM || (slotIndex >= SLOT_SOURCE_ASPECTS_START && slotIndex <= SLOT_SELECTED_ASPECT)) {
            return ItemStack.EMPTY;
        }

        Slot slot = getSlot(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (slotIndex == SLOT_BLANK_PATTERNS || slotIndex == SLOT_ENCODED_PATTERN) {
            // Transfer out to player inventory
            if (!moveItemStackTo(stack, PLAYER_INV_START, TOTAL_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (slotIndex >= PLAYER_INV_START && slotIndex < TOTAL_SLOTS) {
            // From player inventory:
            if (DistillationPatternHelper.isBlankPattern(stack)) {
                if (!moveItemStackTo(stack, SLOT_BLANK_PATTERNS, SLOT_BLANK_PATTERNS + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (DistillationPatternHelper.isValidPattern(stack)) {
                if (!moveItemStackTo(stack, SLOT_ENCODED_PATTERN, SLOT_ENCODED_PATTERN + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // In 1.7.10: Shift-clicking an ordinary item copies it into the ghost source slot without consumption
                if (owner.level().isClientSide) return ItemStack.EMPTY;
                getSlot(SLOT_SOURCE_ITEM).set(stack.copyWithCount(1));
                cachedSource = getSlot(SLOT_SOURCE_ITEM).getItem().copy();
                scanSourceItem(true);
                broadcastChanges();
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index != SLOT_SOURCE_ITEM
                && (slot.index < SLOT_SOURCE_ASPECTS_START || slot.index > SLOT_SELECTED_ASPECT)
                && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (owner != player || player instanceof FakePlayer) return false;
        if (player.level().isClientSide) {
            return blockEntity == null || (!blockEntity.isRemoved() && player.distanceToSqr(
                    blockEntity.getBlockPos().getX() + 0.5,
                    blockEntity.getBlockPos().getY() + 0.5,
                    blockEntity.getBlockPos().getZ() + 0.5) <= 64.0);
        }
        if (blockEntity == null) return false;
        Level level = blockEntity.getLevel();
        if (level == null || level != player.level() || !level.hasChunkAt(blockEntity.getBlockPos())) return false;
        if (blockEntity.isRemoved() || level.getBlockEntity(blockEntity.getBlockPos()) != blockEntity) return false;
        if (!Platform.hasPermissions(new DimensionalBlockPos(level, blockEntity.getBlockPos()), player)) return false;
        return player.distanceToSqr(
                blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
