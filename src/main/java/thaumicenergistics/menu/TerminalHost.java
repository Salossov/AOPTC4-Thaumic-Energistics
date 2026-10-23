package thaumicenergistics.menu;

import appeng.api.config.ViewItems;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ISubMenuHost;
import appeng.api.storage.MEStorage;
import appeng.menu.locator.MenuHostLocator;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.storage.*;

/** Shared original terminal menu over a part, a local chest cell or a portable link. */
public interface TerminalHost extends IActionHost,ISubMenuHost {
    TerminalInventory inventory();
    boolean valid(Player player);
    MEStorage rawStorage();
    IEnergySource transferEnergy();
    List<TerminalEntry> entries();
    int sort();
    appeng.api.config.ViewItems view();
    Aspect selected();
    void selected(Aspect value);
    void rotateSort(boolean backwards);
    void rotateView(boolean backwards);
    default MEStorage poweredStorage(){var raw=rawStorage();return raw==null?null:new PoweredEssentiaStorage(raw,transferEnergy());}
    default boolean supportsCrafting(){return false;}
    default int lockedPlayerSlot(){return -1;}
    default MenuHostLocator craftingLocator(){return null;}
    default void workTick(int elapsed){}
    default void removed(Player player){}
}
