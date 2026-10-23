package thaumicenergistics.knowledge;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;

/**
 * Represents an encoded arcane crafting recipe pattern stored in a Knowledge Core.
 * Parity with 1.7.10 ArcaneCraftingPattern and implements AE2 IPatternDetails.
 */
public class ArcaneCraftingPattern implements IPatternDetails {
    public static final int GRID_SIZE = 9;
    static final String DEFINITION_TAG = "thaumicenergistics:arcane_pattern";
    private final HolderLookup.Provider registries;
    private static final String NBTKEY_INGREDIENT_NUM = "input#";
    private static final String NBTKEY_RESULT = "output";
    private static final String NBTKEY_INGREDIENT_TYPE = "ThE_crafting_type";
    private static final String NBTKEY_ARRAY_SIZE = "size";
    private static final String NBTKEY_ASPECTS = "aspects";
    private static final int NBTKEY_ITEMTYPE = 1;
    private static final int NBTKEY_ARRAYTYPE = 2;

    private ItemStack knowledgeCoreHost = ItemStack.EMPTY;
    private final Object[] ingredients = new Object[GRID_SIZE];
    private ItemStack result = ItemStack.EMPTY;
    private final Map<Aspect, Integer> aspects = new LinkedHashMap<>();
    private boolean valid = false;

    // Cached outputs for AE2
    private List<GenericStack> cachedOutputs = null;
    private IInput[] cachedInputs = null;

    public ArcaneCraftingPattern(ItemStack knowledgeCoreHost, Map<Aspect, Integer> wholeVis, ItemStack result, Object[] inputIngredients, HolderLookup.Provider registries) {
        this.registries = Objects.requireNonNull(registries);
        this.knowledgeCoreHost = knowledgeCoreHost == null ? ItemStack.EMPTY : knowledgeCoreHost.copy();
        this.result = result == null ? ItemStack.EMPTY : result.copy();
        if (wholeVis != null) {
            for (Map.Entry<Aspect, Integer> entry : wholeVis.entrySet()) {
                if (entry.getKey() == null || !entry.getKey().isPrimal() || entry.getValue() == null || entry.getValue() <= 0) return;
                this.aspects.put(entry.getKey(), entry.getValue());
            }
        }
        boolean hasAtLeastOneInput = false;
        if (inputIngredients != null) {
            if (inputIngredients.length > GRID_SIZE) return;
            for (int i = 0; i < GRID_SIZE && i < inputIngredients.length; i++) {
                Object ing = inputIngredients[i];
                if (ing instanceof ItemStack stack && !stack.isEmpty()) {
                    this.ingredients[i] = stack.copy();
                    hasAtLeastOneInput = true;
                } else if (ing instanceof List<?> list && !list.isEmpty()) {
                    if (list.size() > 256) return;
                    List<ItemStack> copiedList = new ArrayList<>();
                    for (Object obj : list) {
                        if (obj instanceof ItemStack st && !st.isEmpty()) {
                            copiedList.add(st.copy());
                        } else return;
                    }
                    if (!copiedList.isEmpty()) {
                        this.ingredients[i] = Collections.unmodifiableList(copiedList);
                        hasAtLeastOneInput = true;
                    }
                } else if (ing != null && !(ing instanceof ItemStack stack && stack.isEmpty())) return;
            }
        }
        this.valid = !this.aspects.isEmpty() && !this.result.isEmpty() && hasAtLeastOneInput;
    }

    public ArcaneCraftingPattern(ItemStack knowledgeCoreHost, CompoundTag data, HolderLookup.Provider registries) {
        this.registries = Objects.requireNonNull(registries);
        this.knowledgeCoreHost = knowledgeCoreHost == null ? ItemStack.EMPTY : knowledgeCoreHost.copy();
        readFromNBT(data, registries);
    }

    public boolean isPatternValid() {
        return valid;
    }

    public ItemStack getResult() {
        return result.copy();
    }

    public ItemStack getKnowledgeCoreHost() {
        return knowledgeCoreHost.copy();
    }

    public void setKnowledgeCoreHost(ItemStack host) {
        this.knowledgeCoreHost = host == null ? ItemStack.EMPTY : host.copy();
    }

    public Map<Aspect, Integer> getAspects() {
        return Map.copyOf(aspects);
    }

    public ArcaneCraftingPattern copy() {
        return new ArcaneCraftingPattern(knowledgeCoreHost, writeToNBT(new CompoundTag(), registries), registries);
    }

    public Object[] getIngredients() {
        Object[] copy = new Object[GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            if (ingredients[i] instanceof ItemStack st) {
                copy[i] = st.copy();
            } else if (ingredients[i] instanceof List<?> list) {
                List<ItemStack> sub = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof ItemStack st) sub.add(st.copy());
                }
                copy[i] = sub;
            }
        }
        return copy;
    }

    public Object getIngredient(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= GRID_SIZE) return null;
        Object ing = ingredients[slotIndex];
        if (ing instanceof ItemStack st) return st.copy();
        if (ing instanceof List<?> list) {
            List<ItemStack> sub = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof ItemStack st) sub.add(st.copy());
            }
            return sub;
        }
        return null;
    }

    public static boolean canSubstituteFor(Object target, ItemStack input) {
        if (target == null || input == null || input.isEmpty()) return false;
        if (target instanceof ItemStack targetStack) {
            return canSubItem(targetStack, input);
        } else if (target instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof ItemStack targetStack && canSubItem(targetStack, input)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canSubItem(ItemStack target, ItemStack input) {
        if (target.isEmpty() || input.isEmpty()) return false;
        if (ItemStack.matches(target, input)) return true;
        if (!target.is(input.getItem())) return false;
        if (target.isDamageableItem()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(target, input);
    }

    public CompoundTag writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        if (data == null) data = new CompoundTag();
        data.putInt("format_version", 1);

        // Write aspects
        CompoundTag aspectsTag = new CompoundTag();
        for (Map.Entry<Aspect, Integer> entry : aspects.entrySet()) {
            aspectsTag.putInt(entry.getKey().getTag(), entry.getValue());
        }
        data.put(NBTKEY_ASPECTS, aspectsTag);

        // Write ingredients
        for (int i = 0; i < GRID_SIZE; i++) {
            Object ing = ingredients[i];
            if (ing == null) continue;
            CompoundTag ingTag = new CompoundTag();
            if (ing instanceof ItemStack single && !single.isEmpty()) {
                ingTag.putInt(NBTKEY_INGREDIENT_TYPE, NBTKEY_ITEMTYPE);
                single.save(registries, ingTag);
                data.put(NBTKEY_INGREDIENT_NUM + i, ingTag);
            } else if (ing instanceof List<?> list && !list.isEmpty()) {
                ingTag.putInt(NBTKEY_INGREDIENT_TYPE, NBTKEY_ARRAYTYPE);
                ingTag.putInt(NBTKEY_ARRAY_SIZE, list.size());
                int itemIdx = 0;
                for (Object obj : list) {
                    if (obj instanceof ItemStack st && !st.isEmpty()) {
                        CompoundTag subTag = new CompoundTag();
                        st.save(registries, subTag);
                        ingTag.put(NBTKEY_INGREDIENT_NUM + itemIdx, subTag);
                        itemIdx++;
                    }
                }
                data.put(NBTKEY_INGREDIENT_NUM + i, ingTag);
            }
        }

        // Write output
        if (!result.isEmpty()) {
            CompoundTag outTag = new CompoundTag();
            result.save(registries, outTag);
            data.put(NBTKEY_RESULT, outTag);
        }
        return data;
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        this.valid = false;
        this.cachedInputs = null;
        this.cachedOutputs = null;
        this.aspects.clear();
        for (int i = 0; i < GRID_SIZE; i++) this.ingredients[i] = null;
        this.result = ItemStack.EMPTY;
        if (data == null || !data.contains("format_version", Tag.TAG_INT) || data.getInt("format_version") != 1) return;

        // Read aspects
        if (data.contains(NBTKEY_ASPECTS, Tag.TAG_COMPOUND)) {
            CompoundTag aspectsTag = data.getCompound(NBTKEY_ASPECTS);
            for (String key : aspectsTag.getAllKeys()) {
                Aspect a = Aspect.getAspect(key);
                int cost = aspectsTag.getInt(key);
                if (a == null || !a.isPrimal() || !aspectsTag.contains(key, Tag.TAG_INT) || cost <= 0) return;
                aspects.put(a, cost);
            }
        }
        if (aspects.isEmpty()) return;

        // Read result
        if (data.contains(NBTKEY_RESULT, Tag.TAG_COMPOUND)) {
            CompoundTag outTag = data.getCompound(NBTKEY_RESULT);
            var opt = ItemStack.parse(registries, outTag);
            if (opt.isPresent()) {
                this.result = opt.get();
            }
        }
        if (result.isEmpty()) return;

        // Read ingredients
        boolean hasAtLeastOne = false;
        for (int i = 0; i < GRID_SIZE; i++) {
            String key = NBTKEY_INGREDIENT_NUM + i;
            if (!data.contains(key)) continue;
            if (!data.contains(key, Tag.TAG_COMPOUND)) return;
            CompoundTag ingTag = data.getCompound(key);
            int type = ingTag.getInt(NBTKEY_INGREDIENT_TYPE);
            if (type == NBTKEY_ITEMTYPE) {
                var opt = ItemStack.parse(registries, ingTag);
                if (opt.isPresent() && !opt.get().isEmpty()) {
                    ingredients[i] = opt.get();
                    hasAtLeastOne = true;
                } else return;
            } else if (type == NBTKEY_ARRAYTYPE) {
                int size = ingTag.getInt(NBTKEY_ARRAY_SIZE);
                if (size > 0 && size <= 256) {
                    List<ItemStack> list = new ArrayList<>();
                    for (int j = 0; j < size; j++) {
                        String subKey = NBTKEY_INGREDIENT_NUM + j;
                        if (ingTag.contains(subKey, Tag.TAG_COMPOUND)) {
                            var opt = ItemStack.parse(registries, ingTag.getCompound(subKey));
                            if (opt.isPresent() && !opt.get().isEmpty()) {
                                list.add(opt.get());
                            } else return;
                        } else return;
                    }
                    if (!list.isEmpty()) {
                        ingredients[i] = Collections.unmodifiableList(list);
                        hasAtLeastOne = true;
                    }
                } else return;
            } else return;
        }
        this.valid = hasAtLeastOne && !result.isEmpty() && !aspects.isEmpty();
    }

    // IPatternDetails Implementation

    @Override
    public AEItemKey getDefinition() {
        if (!valid) throw new IllegalStateException("Invalid arcane pattern");
        // A core contains many recipes. Its complete item key cannot identify one recipe.
        // Keep only this recipe in a private, decoder-recognized definition item.
        ItemStack definition = new ItemStack(KnowledgeRegistrar.KNOWLEDGE_CORE_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.put(DEFINITION_TAG, writeToNBT(new CompoundTag(), registries));
        definition.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return Objects.requireNonNull(AEItemKey.of(definition));
    }

    @Override
    public IInput[] getInputs() {
        if (cachedInputs == null) {
            List<IInput> inputsList = new ArrayList<>();
            for (int i = 0; i < GRID_SIZE; i++) {
                Object ing = ingredients[i];
                if (ing == null) continue;
                List<GenericStack> possibilities = new ArrayList<>();
                if (ing instanceof ItemStack st && !st.isEmpty()) {
                    possibilities.add(GenericStack.fromItemStack(st));
                } else if (ing instanceof List<?> list) {
                    for (Object obj : list) {
                        if (obj instanceof ItemStack st && !st.isEmpty()) {
                            possibilities.add(GenericStack.fromItemStack(st));
                        }
                    }
                }
                if (!possibilities.isEmpty()) {
                    final GenericStack[] arr = possibilities.toArray(new GenericStack[0]);
                    final Object currentIng = ing;
                    inputsList.add(new IInput() {
                        @Override
                        public GenericStack[] getPossibleInputs() {
                            return arr.clone();
                        }

                        @Override
                        public long getMultiplier() {
                            return 1L;
                        }

                        @Override
                        public boolean isValid(AEKey key, Level level) {
                            if (key instanceof AEItemKey itemKey) {
                                return canSubstituteFor(currentIng, itemKey.toStack());
                            }
                            return false;
                        }

                        @Override
                        public AEKey getRemainingKey(AEKey key) {
                            if (key instanceof AEItemKey itemKey) {
                                ItemStack st = itemKey.toStack();
                                ItemStack container = st.getItem().hasCraftingRemainingItem(st)
                                        ? st.getItem().getCraftingRemainingItem(st)
                                        : ItemStack.EMPTY;
                                if (!container.isEmpty()) {
                                    return AEItemKey.of(container);
                                }
                            }
                            return null;
                        }
                    });
                }
            }
            cachedInputs = inputsList.toArray(new IInput[0]);
        }
        return cachedInputs.clone();
    }

    @Override
    public GenericStack getPrimaryOutput() {
        return GenericStack.fromItemStack(result);
    }

    @Override
    public List<GenericStack> getOutputs() {
        if (cachedOutputs == null) {
            List<GenericStack> out = new ArrayList<>();
            if (!result.isEmpty()) {
                out.add(GenericStack.fromItemStack(result));
            }
            cachedOutputs = List.copyOf(out);
        }
        return cachedOutputs;
    }
}
