package thaumicenergistics.storage;

/** Stored and/or craftable native essentia, synchronized only to the owning open menu. */
public record TerminalEntry(EssentiaKey key,long amount,boolean craftable) {
    public TerminalEntry {if(key==null||amount<0)throw new IllegalArgumentException("Invalid terminal entry");}
}
