package thaumicenergistics.storage;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Resource conservation and boundary oracles from the pinned release, not a Minecraft smoke test. */
public final class StorageRulesTest {
    private static int checks;
    public static void main(String[] args) {
        // Release EnumEssentiaStorageTypes: the odd 16K capacity is intentional.
        int[] bytes = {1024, 4096, 16348, 65536, 0};
        double[] drain = {0.5, 1.0, 1.5, 2.0, 0.0};
        for (var tier : CellTier.values()) {
            equal(tier.bytes, bytes[tier.ordinal()], "release byte capacity");
            check(tier.idleDrain == drain[tier.ordinal()], "release idle drain");
            equal(tier.types, tier.creative() ? 63 : 12, "release type count");
            if (!tier.creative()) ordinary(tier);
        }
        creative();
        corruptionAndUnknownAspects();
        System.out.println("THE_STORAGE_RULES_PASS checks=" + checks);
    }
    private static void ordinary(CellTier tier) {
        var cell = new CellLedger(tier, CellContents.EMPTY);
        equal(cell.insert("aer", Long.MAX_VALUE, false), tier.bytes * 2L, "simulation capacity");
        equal(cell.total(), 0, "simulation does not insert");
        equal(cell.insert("aer", Long.MAX_VALUE, true), tier.bytes * 2L, "exact capacity");
        equal(cell.insert("aqua", 1, true), 0, "full insertion");
        equal(cell.usedBytes(), tier.bytes, "full byte count");
        equal(cell.extract("aer", 1, false), 1, "extract simulation result");
        equal(cell.total(), tier.bytes * 2L, "extract simulation does not mutate");
        equal(cell.extract("aer", Long.MAX_VALUE, true), tier.bytes * 2L, "full extraction");
        equal(cell.usedTypes(), 0, "last extraction frees slot");
        cell.insert("aer", 1, true);
        equal(cell.usedBytes(), 1, "odd amount rounds up");
        cell.insert("aqua", 1, true);
        equal(cell.usedBytes(), 1, "two aspects share byte without type tax");
        cell = new CellLedger(tier, CellContents.EMPTY);
        for (int i = 0; i < 12; i++) equal(cell.insert("test:a" + i, 1, true), 1, "first twelve aspects");
        equal(cell.insert("test:thirteenth", 1, true), 0, "thirteenth rejected");
        equal(cell.insert("test:a0", 1, true), 1, "existing type still accepted");
        cell.extract("test:a0", Long.MAX_VALUE, true);
        equal(cell.insert("test:thirteenth", 1, true), 1, "freed type reusable");
        cell.partitionToContents();
        equal(cell.insert("other:excluded", 1, true), 0, "partition excludes new type");
        check(cell.preferred("test:a1"), "partition preference");
        cell.partitions(java.util.stream.IntStream.range(0, 13).mapToObj(i -> "p" + i).toList());
        equal(cell.contents().partitions().size(), 13, "whitelist is independent of twelve stored-type cap");
        var preserved = cell.contents();
        try { cell.partitions(List.of("Invalid Aspect")); throw new AssertionError("invalid aspect accepted"); }
        catch (IllegalArgumentException expected) { check(cell.contents().equals(preserved), "invalid partition change atomic"); }
        cell.partitions(List.of());
        var random = new Random(1130);
        cell = new CellLedger(tier, CellContents.EMPTY);
        long balance = 0;
        for (int i = 0; i < 2000; i++) {
            var before = cell.contents();
            boolean commit = random.nextBoolean();
            boolean insert = random.nextBoolean();
            long amount = random.nextInt(500);
            String tag = "test:" + random.nextInt(20);
            long changed = insert ? cell.insert(tag, amount, commit) : cell.extract(tag, amount, commit);
            check(changed >= 0 && changed <= amount, "bounded transfer");
            if (commit) balance += insert ? changed : -changed;
            else check(cell.contents().equals(before), "random simulation immutable");
            equal(cell.total(), balance, "conserved units");
            check(cell.total() <= tier.bytes * 2L && cell.usedTypes() <= 12, "bounds during random transfers");
        }
        var encoded = CellContents.CODEC.encodeStart(JsonOps.INSTANCE, cell.contents()).getOrThrow();
        var decoded = CellContents.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        check(decoded.equals(cell.contents()), "persistent data round trip");
    }
    private static void creative() {
        var cell = new CellLedger(CellTier.CREATIVE, CellContents.EMPTY);
        equal(cell.extract("aer", 1, true), 0, "unconfigured creative gives nothing");
        cell.partitions(List.of("aer", "aer", "missing:addon"));
        equal(cell.usedTypes(), 3, "original replacement can retain duplicate creative partitions");
        var before = cell.contents();
        equal(cell.extract("aer", Long.MAX_VALUE, true), Long.MAX_VALUE, "creative source");
        equal(cell.insert("aer", Long.MAX_VALUE, true), 0, "creative rejects insertion");
        equal(cell.extract("terra", 1, true), 0, "creative restricts aspects");
        check(cell.contents().equals(before), "creative extraction does not mutate");
        cell.partitionToContents();
        check(cell.contents().partitions().isEmpty(), "original workbench clears creative partition-to-contents");
        equal(CellLedger.CREATIVE_DISPLAY_AMOUNT, 2_000_000_000L, "release creative display amount");
    }
    private static void corruptionAndUnknownAspects() {
        var amounts = new LinkedHashMap<String, Long>(); amounts.put("missing:addon", Long.MAX_VALUE); amounts.put("aer", 4L);
        var contents = new CellContents(amounts, new ArrayList<>());
        amounts.clear();
        equal(contents.amounts().size(), 2, "defensive copy");
        var cell = new CellLedger(CellTier.K1, contents);
        equal(cell.total(), Long.MAX_VALUE, "saturate corrupt sum");
        equal(cell.insert("aer", 10, true), 0, "overcapacity refuses insertion");
        equal(cell.extract("missing:addon", Long.MAX_VALUE, true), Long.MAX_VALUE, "unknown content is retained and extractable");
        equal(cell.total(), 4, "other content survives recovery");
        for (long invalid : new long[] {-1, Long.MIN_VALUE}) {
            try { cell.insert("aer", invalid, true); throw new AssertionError("negative input accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
            try { cell.extract("aer", invalid, true); throw new AssertionError("negative output accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        try { contents.amounts().put("terra", 1L); throw new AssertionError("mutable contents"); }
        catch (UnsupportedOperationException expected) { checks++; }
        var encoded = CellContents.CODEC.encodeStart(JsonOps.INSTANCE, contents).getOrThrow();
        check(CellContents.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().equals(contents), "unknown aspect persistence");
    }
    private static void equal(long actual, long expected, String message) { check(actual == expected, message + ": " + actual + " != " + expected); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
