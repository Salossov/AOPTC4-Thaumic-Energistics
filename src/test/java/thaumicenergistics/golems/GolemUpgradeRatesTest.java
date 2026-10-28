package thaumicenergistics.golems;

import static thaumicenergistics.golems.GolemRulesTest.*;

/**
 * Deferred unit test verifying Golem action rates across Order upgrades and Advanced golem multiplier.
 * Executed by the golemRulesTest main harness during check.
 */
public class GolemUpgradeRatesTest {
    public void testOrderUpgradeClamping() {
        equal(0, GolemNetworkBridge.clampOrderUpgrades(-1));
        equal(0, GolemNetworkBridge.clampOrderUpgrades(0));
        equal(1, GolemNetworkBridge.clampOrderUpgrades(1));
        equal(2, GolemNetworkBridge.clampOrderUpgrades(2));
        equal(2, GolemNetworkBridge.clampOrderUpgrades(5));
    }

    public void testItemRates() {
        // Base golem: 8, 24, 32
        equal(8, GolemNetworkBridge.getItemRate(0, false));
        equal(24, GolemNetworkBridge.getItemRate(1, false));
        equal(32, GolemNetworkBridge.getItemRate(2, false));

        // Advanced golem: 16, 48, 64 (2x)
        equal(16, GolemNetworkBridge.getItemRate(0, true));
        equal(48, GolemNetworkBridge.getItemRate(1, true));
        equal(64, GolemNetworkBridge.getItemRate(2, true));
    }

    public void testFluidRates() {
        // Base golem: 100, 250, 500
        equal(100, GolemNetworkBridge.getFluidRate(0, false));
        equal(250, GolemNetworkBridge.getFluidRate(1, false));
        equal(500, GolemNetworkBridge.getFluidRate(2, false));

        // Advanced golem: 200, 500, 1000 (2x)
        equal(200, GolemNetworkBridge.getFluidRate(0, true));
        equal(500, GolemNetworkBridge.getFluidRate(1, true));
        equal(1000, GolemNetworkBridge.getFluidRate(2, true));
    }

    public void testEssentiaRates() {
        // Base golem: 4, 12, 16
        equal(4, GolemNetworkBridge.getEssentiaRate(0, false));
        equal(12, GolemNetworkBridge.getEssentiaRate(1, false));
        equal(16, GolemNetworkBridge.getEssentiaRate(2, false));

        // Advanced golem: 8, 24, 32 (2x)
        equal(8, GolemNetworkBridge.getEssentiaRate(0, true));
        equal(24, GolemNetworkBridge.getEssentiaRate(1, true));
        equal(32, GolemNetworkBridge.getEssentiaRate(2, true));
    }
}
