package thaumicenergistics.knowledge;

import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.research.ResearchApi;

/**
 * Menu for Knowledge Inscriber.
 * Strictly implements the 68-slot layout, ghost input/selection, dupe protection,
 * server arcane recipe evaluation, and CoreSaveState state machine.
 */
public class KnowledgeInscriberMenu extends AbstractContainerMenu {
    public enum CoreSaveState {
        Disabled_InvalidRecipe,
        Disabled_CoreFull,
        Disabled_MissingCore,
        Enabled_Save,
        Enabled_Delete;

        public static CoreSaveState fromOrdinal(int ordinal) {
            CoreSaveState[] values = values();
            return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : Disabled_MissingCore;
        }
    }

    public static final int KCORE_SLOT = 0;
    public static final int FIRST_PATTERN_SLOT = 1;
    public static final int LAST_PATTERN_SLOT = 21;
    public static final int PATTERN_COUNT = 21;
    public static final int FIRST_MATRIX_SLOT = 22;
    public static final int LAST_MATRIX_SLOT = 30;
    public static final int MATRIX_COUNT = 9;
    public static final int RESULT_SLOT = 31;
    public static final int FIRST_PLAYER_SLOT = 32;
    public static final int LAST_PLAYER_SLOT = 58;
    public static final int FIRST_HOTBAR_SLOT = 59;
    public static final int LAST_HOTBAR_SLOT = 67;

    private final Player owner;
    private final Container inscriber;
    private final SimpleContainer displayInventory = new SimpleContainer(PATTERN_COUNT);
    private final SimpleContainer craftingMatrix = new SimpleContainer(MATRIX_COUNT);
    private final SimpleContainer resultInventory = new SimpleContainer(1);
    private final HandlerKnowledgeCore kCoreHandler = new HandlerKnowledgeCore();

    private RecipeHolder<IArcaneRecipe> activeRecipe = null;
    private CraftingInput.Positioned activePositioned = null;
    private ItemStack activeOutput = ItemStack.EMPTY;
    private CoreSaveState lastSentState = null;

    public KnowledgeInscriberMenu(int containerId, Inventory playerInventory, Container inscriber) {
        super(KnowledgeRegistrar.KNOWLEDGE_INSCRIBER_MENU.get(), containerId);
        this.owner = playerInventory.player;
        this.inscriber = inscriber != null ? inscriber : new SimpleContainer(1);

        // Slot 0: KCore slot at (186, 8)
        this.addSlot(new Slot(this.inscriber, KnowledgeInscriberBlockEntity.KCORE_SLOT, 186, 8) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !stack.isEmpty() && stack.getItem() instanceof ItemKnowledgeCore;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Slots 1..21: Pattern display 3x7 at (26, 18), step 18
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                int x = 26 + col * 18;
                int y = 18 + row * 18;
                this.addSlot(new Slot(displayInventory, index, x, y) {
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
        }

        // Slots 22..30: Ghost crafting matrix 3x3 at (26, 90), step 18
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                int x = 26 + col * 18;
                int y = 90 + row * 18;
                this.addSlot(new Slot(craftingMatrix, index, x, y) {
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
        }

        // Slot 31: Ghost result slot at (116, 108)
        this.addSlot(new Slot(resultInventory, 0, 116, 108) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });

        // Slots 32..58: Player inventory 3x9 at Y = 162
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 162 + row * 18));
            }
        }

        // Slots 59..67: Player hotbar 1x9 at Y = 220
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 220));
        }

        // Initialize core handler if on server
        if (!owner.level().isClientSide) {
            ItemStack core = this.inscriber.getItem(KnowledgeInscriberBlockEntity.KCORE_SLOT);
            if (!core.isEmpty() && core.getItem() instanceof ItemKnowledgeCore) {
                kCoreHandler.open(core, owner.level().registryAccess());
            }
            updatePatternSlots();
            updateActiveRecipe();
        }
    }

    public CoreSaveState getSaveState() {
        if (!kCoreHandler.hasCore()) {
            return CoreSaveState.Disabled_MissingCore;
        }
        if (activeRecipe == null || activeOutput.isEmpty()) {
            return CoreSaveState.Disabled_InvalidRecipe;
        }
        if (kCoreHandler.hasPatternFor(activeOutput)) {
            return CoreSaveState.Enabled_Delete;
        }
        if (!kCoreHandler.hasRoomToStorePattern()) {
            return CoreSaveState.Disabled_CoreFull;
        }
        return CoreSaveState.Enabled_Save;
    }

    public void sendSaveState(Player player, boolean justSaved) {
        if (player instanceof ServerPlayer serverPlayer) {
            CoreSaveState state = getSaveState();
            this.lastSentState = state;
            PacketDistributor.sendToPlayer(serverPlayer, new KnowledgeInscriberStatePayload(this.containerId, state.ordinal(), justSaved));
        }
    }

    private void updatePatternSlots() {
        List<ItemStack> stored = kCoreHandler.hasCore() ? kCoreHandler.getStoredOutputs() : Collections.emptyList();
        for (int i = 0; i < PATTERN_COUNT; i++) {
            displayInventory.setItem(i, i < stored.size() ? stored.get(i).copy() : ItemStack.EMPTY);
        }
    }

    @SuppressWarnings("unchecked")
    private static RecipeType<IArcaneRecipe> arcaneType() {
        return (RecipeType<IArcaneRecipe>) BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse("thaumcraft:arcane"));
    }

    public void updateActiveRecipe() {
        if (owner.level().isClientSide) return;
        List<ItemStack> stacks = new ArrayList<>(MATRIX_COUNT);
        for (int i = 0; i < MATRIX_COUNT; i++) {
            stacks.add(craftingMatrix.getItem(i));
        }
        this.activePositioned = CraftingInput.ofPositioned(3, 3, stacks);
        CraftingInput grid = this.activePositioned.input();

        RecipeType<IArcaneRecipe> type = arcaneType();
        RecipeHolder<IArcaneRecipe> matched = null;
        ItemStack out = ItemStack.EMPTY;

        if (type != null && owner instanceof ServerPlayer serverPlayer) {
            for (RecipeHolder<IArcaneRecipe> holder : owner.level().getRecipeManager().getAllRecipesFor(type)) {
                IArcaneRecipe recipe = holder.value();
                if (recipe.matches(grid, owner.level()) && ResearchApi.knows(serverPlayer, recipe.research()) && recipe.mayCraft(serverPlayer, grid)) {
                    matched = holder;
                    out = recipe.assemble(grid, owner.level().registryAccess());
                    break;
                }
            }
        }
        this.activeRecipe = matched;
        this.activeOutput = out;
        resultInventory.setItem(0, out.copy());
        sendSaveState(owner, false);
    }

    public void handleAction(ServerPlayer player, int action, int patternIndex) {
        if (action == KnowledgeInscriberActionPayload.ACTION_FULL_UPDATE) {
            sendSaveState(player, false);
        } else if (action == KnowledgeInscriberActionPayload.ACTION_SAVE_DELETE) {
            CoreSaveState state = getSaveState();
            if (state == CoreSaveState.Enabled_Save && activeRecipe != null && activePositioned != null && !activeOutput.isEmpty()) {
                Object[] inputs = new Object[MATRIX_COUNT];
                NonNullList<Ingredient> ings = activeRecipe.value().getIngredients();
                int pw = activePositioned.input().width();
                int ph = activePositioned.input().height();
                int pl = activePositioned.left();
                int pt = activePositioned.top();

                for (int i = 0; i < MATRIX_COUNT; i++) {
                    int gx = i % 3;
                    int gy = i / 3;
                    if (gx >= pl && gx < pl + pw && gy >= pt && gy < pt + ph) {
                        int px = gx - pl;
                        int py = gy - pt;
                        int ingIdx = py * pw + px;
                        if (ingIdx >= 0 && ingIdx < ings.size()) {
                            Ingredient ing = ings.get(ingIdx);
                            ItemStack slotStack = craftingMatrix.getItem(i);
                            ItemStack[] possible = ing.getItems();
                            if (possible.length > 1) {
                                List<ItemStack> list = new ArrayList<>();
                                if (!slotStack.isEmpty()) list.add(slotStack.copy());
                                for (ItemStack alt : possible) {
                                    if (list.stream().noneMatch(s -> ItemStack.matches(s, alt))) {
                                        list.add(alt.copy());
                                    }
                                }
                                inputs[i] = list;
                            } else if (!slotStack.isEmpty()) {
                                inputs[i] = slotStack.copy();
                            }
                        }
                    }
                }

                Map<Aspect, Integer> wholeVis = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : activeRecipe.value().vis(activePositioned.input()).entrySet()) {
                    Aspect a = Aspect.getAspect(entry.getKey());
                    if (a != null && a.isPrimal() && entry.getValue() > 0) {
                        wholeVis.put(a, entry.getValue());
                    }
                }

                ItemStack currentCore = inscriber.getItem(KnowledgeInscriberBlockEntity.KCORE_SLOT);
                ArcaneCraftingPattern pattern = new ArcaneCraftingPattern(currentCore, wholeVis, activeOutput, inputs);
                if (pattern.isPatternValid()) {
                    kCoreHandler.addPattern(pattern);
                    updatePatternSlots();
                    inscriber.setChanged();
                    sendSaveState(player, true);
                }
            } else if (state == CoreSaveState.Enabled_Delete && !activeOutput.isEmpty()) {
                ArcaneCraftingPattern p = kCoreHandler.getPatternForItem(activeOutput);
                if (p != null) {
                    kCoreHandler.removePattern(p);
                    updatePatternSlots();
                    inscriber.setChanged();
                    sendSaveState(player, false);
                }
            }
        } else if (action == KnowledgeInscriberActionPayload.ACTION_CLEAR) {
            for (int i = 0; i < MATRIX_COUNT; i++) {
                craftingMatrix.setItem(i, ItemStack.EMPTY);
            }
            updateActiveRecipe();
        } else if (action == KnowledgeInscriberActionPayload.ACTION_SELECT_PATTERN) {
            if (patternIndex >= 0 && patternIndex < PATTERN_COUNT) {
                ItemStack patternOut = displayInventory.getItem(patternIndex);
                if (!patternOut.isEmpty()) {
                    ArcaneCraftingPattern p = kCoreHandler.getPatternForItem(patternOut);
                    if (p != null) {
                        for (int i = 0; i < MATRIX_COUNT; i++) {
                            Object ing = p.getIngredient(i);
                            if (ing instanceof ItemStack st) {
                                craftingMatrix.setItem(i, st.copy());
                            } else if (ing instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ItemStack st) {
                                craftingMatrix.setItem(i, st.copy());
                            } else {
                                craftingMatrix.setItem(i, ItemStack.EMPTY);
                            }
                        }
                        updateActiveRecipe();
                    }
                }
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (!owner.level().isClientSide) {
            ItemStack currentCore = inscriber.getItem(KnowledgeInscriberBlockEntity.KCORE_SLOT);
            if (!kCoreHandler.isHandlingCore(currentCore)) {
                if (!currentCore.isEmpty() && currentCore.getItem() instanceof ItemKnowledgeCore) {
                    kCoreHandler.open(currentCore, owner.level().registryAccess());
                } else {
                    kCoreHandler.close();
                }
                updatePatternSlots();
                updateActiveRecipe();
                sendSaveState(owner, false);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        if (owner != player) return false;
        return inscriber.stillValid(player);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Exploit protection: reject any sweep, throw, swap, clone, or quick craft targeting ghost/display slots
        if (slotId >= FIRST_PATTERN_SLOT && slotId <= RESULT_SLOT) {
            if (clickType == ClickType.SWAP || clickType == ClickType.THROW || clickType == ClickType.CLONE ||
                clickType == ClickType.QUICK_CRAFT || clickType == ClickType.PICKUP_ALL) {
                return;
            }

            // Clicked pattern display slot (1..21)
            if (slotId >= FIRST_PATTERN_SLOT && slotId <= LAST_PATTERN_SLOT) {
                int patternIdx = slotId - FIRST_PATTERN_SLOT;
                ItemStack patternOut = displayInventory.getItem(patternIdx);
                if (!patternOut.isEmpty()) {
                    if (player.level().isClientSide) {
                        PacketDistributor.sendToServer(new KnowledgeInscriberActionPayload(this.containerId, KnowledgeInscriberActionPayload.ACTION_SELECT_PATTERN, patternIdx));
                    } else if (player instanceof ServerPlayer sp) {
                        handleAction(sp, KnowledgeInscriberActionPayload.ACTION_SELECT_PATTERN, patternIdx);
                    }
                }
                return;
            }

            // Clicked crafting matrix slot (22..30)
            if (slotId >= FIRST_MATRIX_SLOT && slotId <= LAST_MATRIX_SLOT) {
                int matrixIdx = slotId - FIRST_MATRIX_SLOT;
                ItemStack carried = getCarried();
                if (!carried.isEmpty()) {
                    craftingMatrix.setItem(matrixIdx, carried.copyWithCount(1));
                } else {
                    craftingMatrix.setItem(matrixIdx, ItemStack.EMPTY);
                }
                updateActiveRecipe();
                return;
            }

            // Clicked result slot (31) - inaccessible
            if (slotId == RESULT_SLOT) {
                return;
            }
        }

        // Standard interaction for slot 0 and player slots
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            // From player inventory / hotbar (32..67)
            if (slotIndex >= FIRST_PLAYER_SLOT && slotIndex <= LAST_HOTBAR_SLOT) {
                if (stackInSlot.getItem() instanceof ItemKnowledgeCore) {
                    // Try to move into KCore slot (0)
                    if (!this.moveItemStackTo(stackInSlot, KCORE_SLOT, KCORE_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex < FIRST_HOTBAR_SLOT) {
                    // Move from inventory to hotbar
                    if (!this.moveItemStackTo(stackInSlot, FIRST_HOTBAR_SLOT, LAST_HOTBAR_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Move from hotbar to inventory
                    if (!this.moveItemStackTo(stackInSlot, FIRST_PLAYER_SLOT, LAST_PLAYER_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            // From KCore slot (0)
            else if (slotIndex == KCORE_SLOT) {
                if (!this.moveItemStackTo(stackInSlot, FIRST_PLAYER_SLOT, LAST_HOTBAR_SLOT + 1, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // From any ghost or display slot (1..31) -> strictly prohibited
            else {
                return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stackInSlot);
        }

        return itemstack;
    }
}
