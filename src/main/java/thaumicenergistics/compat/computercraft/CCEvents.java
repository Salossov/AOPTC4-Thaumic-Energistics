package thaumicenergistics.compat.computercraft;

/**
 * Event IDs for the ComputerCraft "essentia" event matching original Thaumic Energistics 1.7.10.
 */
public enum CCEvents {
    AspectUpdate(1),
    PowerChange(2),
    Detached(3);

    public final int eventID;

    CCEvents(int id) {
        this.eventID = id;
    }
}
