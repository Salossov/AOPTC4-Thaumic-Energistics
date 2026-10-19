package thaumicenergistics.api;

import appeng.api.networking.IGrid;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import thaumcraft.api.aspects.Aspect;

/** A grid-bound centivis endpoint; quantities use Thaumcraft's existing centivis units. */
public interface DigiVisSource {
    int consumeVis(Aspect primal, int requestedCentivis);
    IGrid getGrid();
    GlobalPos getLocation();
    Direction getSide();
    long getUID();
    boolean isActive();
}
