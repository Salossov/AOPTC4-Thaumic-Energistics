package thaumicenergistics.assembler;

import appeng.api.upgrades.IUpgradeInventory;
import net.minecraft.world.entity.player.Player;

/** Menu access to the concrete loaded machine; no client-side crafting authority. */
public interface AssemblerHost {
    AssemblerInventory inventory();
    IUpgradeInventory upgrades();
    boolean valid(Player player);
    AssemblerStatePayload snapshot(int menuId);
}
