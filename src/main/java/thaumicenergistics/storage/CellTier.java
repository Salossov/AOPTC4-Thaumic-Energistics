package thaumicenergistics.storage;

import net.minecraft.world.item.Rarity;

/**
 * Port of 1.1.3.0 EnumEssentiaStorageTypes.
 * Preserves original tier ordering: 1k, 4k, 16k, 64k, creative.
 * Preserves original 16k capacity quirk: 16348 bytes (not 16384).
 */
public enum CellTier {
    K1(0, "1k", 1024, 12, 0.5, Rarity.COMMON),
    K4(1, "4k", 4096, 12, 1.0, Rarity.UNCOMMON),
    // Quirk kept from 1.1.3.0 release bytecode and upstream: exactly 16348 bytes, not 16384.
    K16(2, "16k", 16348, 12, 1.5, Rarity.UNCOMMON),
    K64(3, "64k", 65536, 12, 2.0, Rarity.RARE),
    CREATIVE(4, "creative", 0, 63, 0.0, Rarity.EPIC);

    public final int index;
    public final String suffix;
    public final int bytes;
    public final int types;
    public final double idleDrain;
    public final Rarity rarity;

    CellTier(int index, String suffix, int bytes, int types, double idleDrain, Rarity rarity) {
        this.index = index;
        this.suffix = suffix;
        this.bytes = bytes;
        this.types = types;
        this.idleDrain = idleDrain;
        this.rarity = rarity;
    }

    /** Original HandlerItemEssentiaCell.ESSENTIA_PER_BYTE = 2; no per-type byte tax. */
    public long capacity() {
        return creative() ? 0L : bytes * 2L;
    }

    public boolean creative() {
        return this == CREATIVE;
    }

    public boolean hasComponent() {
        return !creative();
    }

    public int maxStoredTypes() {
        return types;
    }

    public static CellTier byIndex(int index) {
        if (index < 0 || index >= values().length) {
            return K1;
        }
        return values()[index];
    }

    public static CellTier bySuffix(String suffix) {
        if (suffix == null) return K1;
        for (CellTier tier : values()) {
            if (tier.suffix.equalsIgnoreCase(suffix)) {
                return tier;
            }
        }
        return K1;
    }
}
