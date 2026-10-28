package thaumicenergistics.golems;

import static thaumicenergistics.golems.GolemRulesTest.*;

/**
 * Deferred unit test verifying Gearbox power division, accumulation thresholds,
 * integer arithmetic, and side bitmask encoding.
 * Executed by the golemRulesTest main harness during check.
 */
public class GearboxPowerDistributionTest {
    public void testIntegerPowerDivision() {
        int basePower = GearboxBlockEntity.BASE_POWER;
        equal(6, basePower);

        // 1 to 6 attached active crankables:
        int[] expected = {6, 3, 2, 1, 1, 1};
        for (int count = 1; count <= 6; count++) {
            int power = basePower / count;
            equal(expected[count - 1], power, "Divisor " + count);
        }
    }

    public void testThresholdAccumulation() {
        int required = GearboxBlockEntity.REQUIRED_POWER;
        equal(18, required);

        // Single crankable gets 6 power per turn -> 3 turns needed
        int shaft = 0;
        int turns = 0;
        for (int crank = 1; crank <= 10; crank++) {
            shaft += 6;
            if (shaft >= required) {
                shaft = 0;
                turns++;
            }
        }
        equal(3, turns);
        equal(6, shaft); // Remainder after 10th crank
    }

    public void testSideMaskEncoding() {
        byte mask = 0;
        for (int i = 0; i < 6; i++) {
            mask |= (byte) (1 << i);
        }
        equal(0x3F, mask & 0xFF); // 6 bits set: 0b00111111
    }
}
