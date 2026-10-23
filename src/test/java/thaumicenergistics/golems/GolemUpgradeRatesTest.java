package thaumicenergistics.golems;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Deferred unit test verifying Golem action rates across Order upgrades and Advanced golem multiplier.
 * (DO NOT RUN per prompt constraints: deferred for future test suite run).
 */
public class GolemUpgradeRatesTest {
    @Test
    public void testOrderUpgradeClamping() {
        Assertions.assertEquals(0, GolemNetworkBridge.clampOrderUpgrades(-1));
        Assertions.assertEquals(0, GolemNetworkBridge.clampOrderUpgrades(0));
        Assertions.assertEquals(1, GolemNetworkBridge.clampOrderUpgrades(1));
        Assertions.assertEquals(2, GolemNetworkBridge.clampOrderUpgrades(2));
        Assertions.assertEquals(2, GolemNetworkBridge.clampOrderUpgrades(5));
    }

    @Test
    public void testItemRates() {
        // Base golem: 8, 24, 32
        Assertions.assertEquals(8, GolemNetworkBridge.getItemRate(0, false));
        Assertions.assertEquals(24, GolemNetworkBridge.getItemRate(1, false));
        Assertions.assertEquals(32, GolemNetworkBridge.getItemRate(2, false));

        // Advanced golem: 16, 48, 64 (2x)
        Assertions.assertEquals(16, GolemNetworkBridge.getItemRate(0, true));
        Assertions.assertEquals(48, GolemNetworkBridge.getItemRate(1, true));
        Assertions.assertEquals(64, GolemNetworkBridge.getItemRate(2, true));
    }

    @Test
    public void testFluidRates() {
        // Base golem: 100, 250, 500
        Assertions.assertEquals(100, GolemNetworkBridge.getFluidRate(0, false));
        Assertions.assertEquals(250, GolemNetworkBridge.getFluidRate(1, false));
        Assertions.assertEquals(500, GolemNetworkBridge.getFluidRate(2, false));

        // Advanced golem: 200, 500, 1000 (2x)
        Assertions.assertEquals(200, GolemNetworkBridge.getFluidRate(0, true));
        Assertions.assertEquals(500, GolemNetworkBridge.getFluidRate(1, true));
        Assertions.assertEquals(1000, GolemNetworkBridge.getFluidRate(2, true));
    }

    @Test
    public void testEssentiaRates() {
        // Base golem: 4, 12, 16
        Assertions.assertEquals(4, GolemNetworkBridge.getEssentiaRate(0, false));
        Assertions.assertEquals(12, GolemNetworkBridge.getEssentiaRate(1, false));
        Assertions.assertEquals(16, GolemNetworkBridge.getEssentiaRate(2, false));

        // Advanced golem: 8, 24, 32 (2x)
        Assertions.assertEquals(8, GolemNetworkBridge.getEssentiaRate(0, true));
        Assertions.assertEquals(24, GolemNetworkBridge.getEssentiaRate(1, true));
        Assertions.assertEquals(32, GolemNetworkBridge.getEssentiaRate(2, true));
    }
}
