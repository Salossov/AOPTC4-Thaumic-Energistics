package thaumicenergistics.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Port of HandlerItemEssentiaCell and HandlerItemEssentiaCellCreative arithmetic and partition rules.
 * Independent of Minecraft engine state.
 */
public final class CellLedger {
    public static final long CREATIVE_DISPLAY_AMOUNT = 2_000_000_000L;
    private final CellTier tier;
    private CellContents contents;

    public CellLedger(CellTier tier, CellContents contents) {
        this.tier = Objects.requireNonNull(tier, "tier cannot be null");
        this.contents = Objects.requireNonNull(contents, "contents cannot be null");
    }

    public CellTier tier() {
        return tier;
    }

    public CellContents contents() {
        return contents;
    }

    /**
     * Total stored essentia across all aspects.
     * Always 0 for creative cells.
     * Overflow-safe against Long.MAX_VALUE without wrap-around.
     */
    public long total() {
        if (tier.creative()) {
            return 0L;
        }
        long result = 0;
        for (long value : contents.amounts().values()) {
            if (value <= 0) continue;
            // Retain overcapacity contents but refuse overflow wrap-around
            if (Long.MAX_VALUE - result < value) {
                return Long.MAX_VALUE;
            }
            result += value;
        }
        return result;
    }

    /**
     * Used bytes calculation:
     * HandlerItemEssentiaCell.getUsedBytes: Math.ceil(usedEssentiaStorage / 2.0).
     * Creative cells always report 0 used bytes.
     * Overflow-safe integer ceiling division: (amount / 2) + (amount % 2).
     */
    public long usedBytes() {
        if (tier.creative()) {
            return 0L;
        }
        long amount = total();
        if (amount <= 0) {
            return 0L;
        }
        return (amount / 2L) + (amount % 2L);
    }

    public long totalBytes() {
        return tier.bytes;
    }

    public long freeBytes() {
        if (tier.creative()) {
            return 0L;
        }
        return Math.max(0L, tier.bytes - usedBytes());
    }

    /**
     * Occupied types count:
     * For normal cells: count of currently stored aspects.
     * For creative cells: count of whitelist partitions (HandlerItemEssentiaCellCreative.getUsedTypes).
     */
    public int usedTypes() {
        return tier.creative() ? contents.partitions().size() : contents.amounts().size();
    }

    public int totalTypes() {
        return tier.types;
    }

    public boolean isPartitioned() {
        return tier.creative() || !contents.partitions().isEmpty();
    }

    public boolean accepts(String tag) {
        if (tier.creative()) {
            return false;
        }
        return contents.partitions().isEmpty() || contents.partitions().contains(tag);
    }

    public boolean preferred(String tag) {
        if (tier.creative() || !accepts(tag)) {
            return false;
        }
        return contents.partitions().contains(tag) || contents.amounts().containsKey(tag);
    }

    /**
     * Attempts to insert essentia.
     * Returns strictly the accepted units count (not the remainder!).
     * Enforces:
     * - Negative requests are invalid; zero returns 0
     * - Creative cells reject all inserts (return 0)
     * - Partition whitelist checks
     * - Type slot limits (max 12 for normal cells)
     * - Byte capacity limits (overcapacity cells reject new inserts without free space)
     */
    public long insert(String tag, long requested, boolean commit) {
        if (requested < 0) throw new IllegalArgumentException("Negative essentia insertion");
        if (requested <= 0) {
            return 0L;
        }
        CellContents.requireTag(tag);
        if (!accepts(tag)) {
            return 0L;
        }
        if (!contents.amounts().containsKey(tag) && contents.amounts().size() >= tier.types) {
            return 0L;
        }
        long currentTotal = total();
        if (currentTotal >= tier.capacity()) {
            return 0L;
        }
        long remaining = tier.capacity() - currentTotal;
        long accepted = Math.min(requested, remaining);
        if (accepted > 0 && commit) {
            var next = new LinkedHashMap<>(contents.amounts());
            var slots = new ArrayList<>(contents.slots());
            if (!next.containsKey(tag)) {
                int empty = slots.indexOf("");
                if (empty < 0) {
                    slots.add(tag);
                } else {
                    slots.set(empty, tag);
                }
            }
            next.put(tag, next.getOrDefault(tag, 0L) + accepted);
            contents = new CellContents(next, contents.partitions(), slots);
        }
        return accepted;
    }

    /**
     * Attempts to extract essentia.
     * Returns strictly the extracted units count.
     * Enforces:
     * - Negative requests are invalid; zero returns 0
     * - Creative cells provide exact requested amount if aspect is in partition list, with zero accumulation/depletion
     * - Exact or excess extraction frees type slot, resets slot index to empty string "", and preserves slot layout
     */
    public long extract(String tag, long requested, boolean commit) {
        if (requested < 0) throw new IllegalArgumentException("Negative essentia extraction");
        if (requested <= 0) {
            return 0L;
        }
        CellContents.requireTag(tag);
        if (tier.creative()) {
            return contents.partitions().contains(tag) ? requested : 0L;
        }
        long stored = contents.amounts().getOrDefault(tag, 0L);
        if (stored <= 0) {
            return 0L;
        }
        long extracted = Math.min(stored, requested);
        if (extracted > 0 && commit) {
            var next = new LinkedHashMap<>(contents.amounts());
            var slots = new ArrayList<>(contents.slots());
            if (stored == extracted) {
                next.remove(tag);
                int idx = slots.indexOf(tag);
                if (idx >= 0) {
                    slots.set(idx, "");
                }
            } else {
                next.put(tag, stored - extracted);
            }
            contents = new CellContents(next, contents.partitions(), slots);
        }
        return extracted;
    }

    public void partitions(List<String> tags) {
        contents = new CellContents(contents.amounts(), tags, contents.slots());
    }

    public boolean addPartition(String tag) {
        CellContents.requireTag(tag);
        if (!contents.partitions().contains(tag)) {
            var next = new ArrayList<>(contents.partitions());
            next.add(tag);
            contents = new CellContents(contents.amounts(), next, contents.slots());
            return true;
        }
        return false;
    }

    public boolean removePartition(String tag) {
        if (contents.partitions().contains(tag)) {
            var next = new ArrayList<>(contents.partitions());
            next.remove(tag);
            contents = new CellContents(contents.amounts(), next, contents.slots());
            return true;
        }
        return false;
    }

    public void clearPartitions() {
        contents = new CellContents(contents.amounts(), List.of(), contents.slots());
    }

    /**
     * Matches 1.7.10 workbench behavior:
     * Partitions the cell to its current contents.
     * Note: for creative cells this clears the partition configuration, matching the original 1.7.10 quirk!
     */
    public void partitionToContents() {
        partitions(List.copyOf(contents.amounts().keySet()));
    }
}
