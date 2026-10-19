package thaumicenergistics.storage;

/** Directly adapted from 1.1.3.0 EnumEssentiaStorageTypes. */
public enum CellTier {
    K1("1k", 1024, 12, 0.5),
    K4("4k", 4096, 12, 1.0),
    // Quirk kept: both the release bytecode and upstream specify 16348, not 16384.
    K16("16k", 16348, 12, 1.5),
    K64("64k", 65536, 12, 2.0),
    CREATIVE("creative", 0, 63, 0.0);

    public final String suffix;
    public final int bytes;
    public final int types;
    public final double idleDrain;

    CellTier(String suffix, int bytes, int types, double idleDrain) {
        this.suffix = suffix;
        this.bytes = bytes;
        this.types = types;
        this.idleDrain = idleDrain;
    }

    /** Original HandlerItemEssentiaCell.ESSENTIA_PER_BYTE = 2; no per-type byte tax. */
    public long capacity() { return bytes * 2L; }
    public boolean creative() { return this == CREATIVE; }
}
