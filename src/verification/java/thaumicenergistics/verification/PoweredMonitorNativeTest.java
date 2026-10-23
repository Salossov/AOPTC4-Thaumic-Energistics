package thaumicenergistics.verification;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.*;

/** Original general EssentiaMonitor rules reused by conversion monitors/terminals.
 * These are intentionally different from storage-bus container power rules.
 */
@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class PoweredMonitorNativeTest {
    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeOriginalMonitorPowerQuirks(GameTestHelper h){
        VerificationReports.install();
        var water=EssentiaKey.of(Aspects.WATER);var earth=EssentiaKey.of(Aspects.EARTH);var source=IActionSource.empty();
        var cell=new EssentiaCell(new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get()),null);
        cell.insert(water,1,Actionable.MODULATE,source);
        var budget=new double[]{.6};var debited=new double[]{0};
        appeng.api.networking.energy.IEnergySource energy=(requested,mode,multiplier)->{
            double result=Math.min(requested,budget[0]);
            if(mode==Actionable.MODULATE){budget[0]-=result;debited[0]+=result;}
            return result;
        };
        var monitor=new PoweredEssentiaStorage(cell,energy);
        h.assertTrue(monitor.extract(water,64,Actionable.SIMULATE,source)==0&&debited[0]==0,
                "release extraction checks .3 times entire requested64, although only one unit exists");
        h.assertTrue(monitor.extract(water,64,Actionable.MODULATE,source)==0&&cell.ledger().total()==1,
                "insufficient full-request power leaves source and budget untouched");
        budget[0]=20;
        h.assertTrue(monitor.extract(water,64,Actionable.SIMULATE,source)==1&&debited[0]==0&&cell.ledger().total()==1,
                "successful power simulation still leaves energy and source untouched");
        h.assertTrue(monitor.extract(water,64,Actionable.MODULATE,source)==1&&Math.abs(debited[0]-.3)<1e-9,
                "modulation charges actual one unit, not simulated full request");
        cell.insert(earth,2047,Actionable.MODULATE,source);budget[0]=.3;debited[0]=0;
        h.assertTrue(monitor.insert(water,64,Actionable.SIMULATE,source)==1&&debited[0]==0&&cell.ledger().total()==2047,
                "release insertion checks power only for amount the network can accept");
        h.assertTrue(monitor.insert(water,64,Actionable.MODULATE,source)==1&&cell.ledger().total()==2048&&Math.abs(debited[0]-.3)<1e-9,
                "partial network insertion charges exactly actual one unit");
        h.assertTrue(monitor.insert(water,8,Actionable.MODULATE,source)==0&&cell.ledger().total()==2048,
                "full storage cannot consume any further essentia");
        cell.extract(earth,8,Actionable.MODULATE,source);budget[0]=.3;debited[0]=0;
        h.assertTrue(monitor.insert(water,8,Actionable.MODULATE,source)==0&&cell.ledger().total()==2040&&debited[0]==0,
                "release does not automatically scale an eight-unit insert down to its affordable one unit");
        System.out.println("THE_NATIVE_ORIGINAL_MONITOR_POWER_PASS");h.succeed();
    }
}
