package thaumicenergistics.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Handles an {@link ItemKnowledgeCore}.
 * Strict parity with 1.7.10 HandlerKnowledgeCore: 21 pattern limit, exact stack matching,
 * NBT serialization into DataComponents.CUSTOM_DATA.
 */
public class HandlerKnowledgeCore {
    public static final String NBTKEY_PATTERNS = "Patterns";
    public static final int MAXIMUM_STORED_PATTERNS = 21;

    private final ArrayList<ArcaneCraftingPattern> patterns = new ArrayList<>(MAXIMUM_STORED_PATTERNS);
    private ItemStack kCore = ItemStack.EMPTY;
    private HolderLookup.Provider registries;
    private ItemStack snapshot = ItemStack.EMPTY;
    private boolean writable;

    public HandlerKnowledgeCore() {
    }

    public HandlerKnowledgeCore(ItemStack kCore, HolderLookup.Provider registries) {
        this.open(kCore, registries);
    }

    public void open(ItemStack kCore, HolderLookup.Provider registries) {
        this.close();
        if (kCore != null && kCore.getItem() instanceof ItemKnowledgeCore) {
            this.kCore = kCore;
            this.registries = registries;
            this.loadKCoreData();
        }
    }

    public void close() {
        this.kCore = ItemStack.EMPTY;
        this.registries = null;
        this.patterns.clear();
        this.snapshot = ItemStack.EMPTY;
        this.writable = false;
    }

    public boolean hasCore() {
        return !this.kCore.isEmpty();
    }

    public ItemStack getCore() {
        return this.kCore.copy();
    }

    public boolean isHandlingCore(ItemStack candidate) {
        if (this.kCore.isEmpty() || candidate == null || candidate.isEmpty()) {
            return false;
        }
        if (!(candidate.getItem() instanceof ItemKnowledgeCore)) {
            return false;
        }
        return this.kCore == candidate && ItemStack.matches(this.snapshot, candidate);
    }

    public boolean canModify() {
        return writable && isHandlingCore(kCore);
    }

    public boolean hasRoomToStorePattern() {
        return canModify() && this.patterns.size() < MAXIMUM_STORED_PATTERNS;
    }

    public boolean hasPatternFor(ItemStack resultStack) {
        return this.getPatternForItem(resultStack) != null;
    }

    public ArcaneCraftingPattern getPatternForItem(ItemStack resultStack) {
        if (resultStack == null || resultStack.isEmpty()) {
            return null;
        }
        for (ArcaneCraftingPattern p : this.patterns) {
            if (p != null && p.isPatternValid()) {
                ItemStack patternResult = p.getResult();
                // Source rule: exact item equality matching item, count, and components
                if (ItemStack.matches(patternResult, resultStack)) {
                    return p.copy();
                }
            }
        }
        return null;
    }

    public List<ArcaneCraftingPattern> getPatterns() {
        return this.patterns.stream().map(ArcaneCraftingPattern::copy).toList();
    }

    public List<ItemStack> getStoredOutputs() {
        List<ItemStack> outputs = new ArrayList<>(this.patterns.size());
        for (ArcaneCraftingPattern p : this.patterns) {
            if (p != null && p.isPatternValid()) {
                outputs.add(p.getResult());
            }
        }
        return outputs;
    }

    public void addPattern(ArcaneCraftingPattern pattern) {
        if (pattern == null || !pattern.isPatternValid() || !this.hasRoomToStorePattern()) {
            return;
        }
        // Duplicate check based on output stack identity
        if (this.getPatternForItem(pattern.getResult()) == null) {
            ArcaneCraftingPattern stored = pattern.copy();
            stored.setKnowledgeCoreHost(this.kCore);
            this.patterns.add(stored);
            this.saveKCoreData();
        }
    }

    public void removePattern(ArcaneCraftingPattern pattern) {
        if (pattern == null || !canModify()) {
            return;
        }
        // Remove matching pattern by result equality
        boolean removed = this.patterns.removeIf(p -> p == pattern || ItemStack.matches(p.getResult(), pattern.getResult()));
        if (removed) {
            this.saveKCoreData();
        }
    }

    private void loadKCoreData() {
        this.patterns.clear();
        if (this.kCore.isEmpty() || this.registries == null) {
            return;
        }
        CustomData customData = this.kCore.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag data = customData.copyTag();

        this.snapshot = this.kCore.copy();
        this.writable = (!data.contains("format_version") ||
                (data.contains("format_version", Tag.TAG_INT) && data.getInt("format_version") == 1));
        if (!writable) return;
        if (data.contains(NBTKEY_PATTERNS) && !data.contains(NBTKEY_PATTERNS, Tag.TAG_LIST)) {
            writable = false;
            return;
        }

        if (data.contains(NBTKEY_PATTERNS, Tag.TAG_LIST)) {
            ListTag raw = (ListTag) data.get(NBTKEY_PATTERNS);
            if ((!raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND) || raw.size() > MAXIMUM_STORED_PATTERNS) {
                writable = false;
                return;
            }
            ListTag plist = data.getList(NBTKEY_PATTERNS, Tag.TAG_COMPOUND);
            int count = Math.min(plist.size(), MAXIMUM_STORED_PATTERNS);
            for (int i = 0; i < count; i++) {
                try {
                    CompoundTag patternTag = plist.getCompound(i);
                    ArcaneCraftingPattern pattern = new ArcaneCraftingPattern(this.kCore, patternTag, this.registries);
                    if (pattern.isPatternValid()) {
                        this.patterns.add(pattern);
                    } else writable = false;
                } catch (Exception ignored) {
                    // Reading other valid entries is safe; rewriting would erase unknown data.
                    writable = false;
                }
            }
        }
    }

    private void saveKCoreData() {
        if (this.kCore.isEmpty() || this.registries == null || !canModify()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, this.kCore, tag -> {
            tag.putInt("format_version", 1);
            ListTag plist = new ListTag();
            for (ArcaneCraftingPattern p : this.patterns) {
                if (p != null && p.isPatternValid()) {
                    plist.add(p.writeToNBT(new CompoundTag(), this.registries));
                }
            }
            if (!plist.isEmpty()) {
                tag.put(NBTKEY_PATTERNS, plist);
            } else {
                tag.remove(NBTKEY_PATTERNS);
            }
        });
        this.snapshot = this.kCore.copy();
    }
}
