package thaumicenergistics.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Port of HandlerItemEssentiaCell's arithmetic and partition rules, independent of Minecraft. */
public final class CellLedger {
    public static final long CREATIVE_DISPLAY_AMOUNT = 2_000_000_000L;
    private final CellTier tier;
    private CellContents contents;

    public CellLedger(CellTier tier, CellContents contents) {
        this.tier = Objects.requireNonNull(tier);
        this.contents = Objects.requireNonNull(contents);
    }

    public CellContents contents() { return contents; }
    public long total() {
        long result = 0;
        for (long value : contents.amounts().values()) {
            // Retain malformed overcapacity contents but refuse more inserts, without wrapping.
            if (Long.MAX_VALUE - result < value) return Long.MAX_VALUE;
            result += value;
        }
        return result;
    }
    public long usedBytes() { long amount = total(); return amount / 2 + amount % 2; }
    public int usedTypes() { return tier.creative() ? contents.partitions().size() : contents.amounts().size(); }
    public boolean accepts(String tag) {
        return !tier.creative() && (contents.partitions().isEmpty() || contents.partitions().contains(tag));
    }
    public boolean preferred(String tag) {
        return !tier.creative() && (contents.partitions().contains(tag) || contents.amounts().containsKey(tag));
    }

    /** Returns accepted units, matching modern MEStorage rather than the old remainder convention. */
    public long insert(String tag, long requested, boolean commit) {
        CellContents.requireTag(tag);
        if (requested < 0) throw new IllegalArgumentException("Negative insertion");
        if (requested == 0 || !accepts(tag)) return 0;
        if (!contents.amounts().containsKey(tag) && contents.amounts().size() >= tier.types) return 0;
        long accepted = Math.min(requested, Math.max(0, tier.capacity() - total()));
        if (accepted > 0 && commit) {
            var next = new LinkedHashMap<>(contents.amounts());
            next.put(tag, next.getOrDefault(tag, 0L) + accepted);
            contents = new CellContents(next, contents.partitions());
        }
        return accepted;
    }

    public long extract(String tag, long requested, boolean commit) {
        CellContents.requireTag(tag);
        if (requested < 0) throw new IllegalArgumentException("Negative extraction");
        if (tier.creative()) return contents.partitions().contains(tag) ? requested : 0;
        long stored = contents.amounts().getOrDefault(tag, 0L);
        long extracted = Math.min(stored, requested);
        if (extracted > 0 && commit) {
            var next = new LinkedHashMap<>(contents.amounts());
            if (stored == extracted) next.remove(tag); else next.put(tag, stored - extracted);
            contents = new CellContents(next, contents.partitions());
        }
        return extracted;
    }

    public void partitions(List<String> tags) {
        // The original 12-type cap limits stored contents, not the partition whitelist.
        contents = new CellContents(contents.amounts(), tags);
    }

    public void partitionToContents() {
        // The original workbench uses the ordinary handler even for creative cells:
        // partition-to-contents clears their configuration because they store no real units.
        partitions(List.copyOf(contents.amounts().keySet()));
    }
}
