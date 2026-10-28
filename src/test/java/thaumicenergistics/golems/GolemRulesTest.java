package thaumicenergistics.golems;

import java.util.Objects;

/** Runs the existing agent-authored constant/rate checks using this project's main harness. */
public final class GolemRulesTest {
    private static int checks;
    public static void main(String[] args) {
        var gearbox = new GearboxPowerDistributionTest();
        gearbox.testIntegerPowerDivision();
        gearbox.testThresholdAccumulation();
        gearbox.testSideMaskEncoding();
        var skins = new GolemBackpackSkinMappingTest();
        skins.testSkinEnumIntegrity();
        skins.testMaterialStringMapping();
        skins.testTexturePathsAreLowercase();
        var rates = new GolemUpgradeRatesTest();
        rates.testOrderUpgradeClamping();
        rates.testItemRates();
        rates.testFluidRates();
        rates.testEssentiaRates();
        System.out.println("THE_GOLEM_RULES_PASS checks=" + checks);
    }
    static void equal(Object expected, Object actual) { equal(expected, actual, "Value mismatch"); }
    static void equal(Object expected, Object actual, String message) {
        require(Objects.equals(expected, actual), message + ": expected=" + expected + ", actual=" + actual);
    }
    static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private GolemRulesTest() {}
}
