package thaumicenergistics.items;

import appeng.api.upgrades.*;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.*;

/** Original fixed battery; approved native AE2 AP linking, without modern upgrade/cursor-insertion extras. */
public final class WirelessEssentiaTerminalItem extends WirelessTerminalItem {
    public static final double CAPACITY=1_600_000;
    public static double GLOBAL_POWER_MULTIPLIER=appeng.api.config.PowerMultiplier.CONFIG.multiplier;
    public WirelessEssentiaTerminalItem(){super(()->CAPACITY,new Properties().stacksTo(1));}
    @Override public boolean isBarVisible(ItemStack stack){return true;}
    @Override public boolean isFoil(ItemStack stack){return false;}
    @Override public IUpgradeInventory getUpgrades(ItemStack stack){return UpgradeInventories.empty();}
    @Override public net.minecraft.world.inventory.MenuType<?> getMenuType(){return ThaumicEnergistics.TERMINAL_MENU.get();}
    @Override public WirelessTerminalHost getMenuHost(Player player,ItemMenuHostLocator locator,BlockHitResult hit){return new WirelessTerminalHost(this,player,locator);}
    @Override public boolean openFromInventory(Player player,ItemMenuHostLocator locator){
        if(player.level().isClientSide||!locator.locateItem(player).is(this))return false;
        var host=getMenuHost(player,locator,null);
        if(!host.valid(player))return false;
        return player.openMenu(new SimpleMenuProvider((id,inv,owner)->new EssentiaTerminalMenu(id,inv,host),Component.translatable("item.thaumicenergistics.essentia_terminal"))).isPresent();
    }
    @Override protected boolean openFromInventory(Player player,ItemMenuHostLocator locator,boolean returning){return openFromInventory(player,locator);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        boolean opened=level.isClientSide||openFromInventory(player,MenuLocators.forHand(player,hand));
        return opened?InteractionResultHolder.sidedSuccess(stack,level.isClientSide):InteractionResultHolder.fail(stack);
    }
    @Override public boolean overrideStackedOnOther(ItemStack stack,net.minecraft.world.inventory.Slot slot,net.minecraft.world.inventory.ClickAction action,Player player){return false;}
    @Override public boolean overrideOtherStackedOnMe(ItemStack stack,ItemStack other,net.minecraft.world.inventory.Slot slot,net.minecraft.world.inventory.ClickAction action,Player player,net.minecraft.world.entity.SlotAccess access){return false;}
}
