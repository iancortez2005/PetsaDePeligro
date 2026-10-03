package petsa.model;

import java.util.Arrays;
import java.util.List;

/**
 * The part-time job, reached through the Door on the Night of Days 7, 14,
 * 21, 24 and 28. It's locked while Academic Performance is 65% or lower
 * (see Player.isPartTimeLocked()), and working a shift at 100% Stress gets
 * the player pickpocketed instead of paid (GameEngine.workPartTime() checks
 * that).
 */
public final class PartTimeJob {

    public static final List<Integer> AVAILABLE_DAYS = Arrays.asList(7, 14, 21, 24, 28);

    private PartTimeJob() {
        // static utility class - not instantiable
    }

    public static boolean isAvailableOn(int day) {
        return AVAILABLE_DAYS.contains(day);
    }

    /** The two shifts: Overtime pays more but costs far more Stress. */
    public enum ShiftType {
        STANDARD(500, 15),
        OVERTIME(750, 40);

        private final int earnings;
        private final int stressCost;

        ShiftType(int earnings, int stressCost) {
            this.earnings = earnings;
            this.stressCost = stressCost;
        }

        public int getEarnings() {
            return earnings;
        }

        public int getStressCost() {
            return stressCost;
        }

        /**
         * Works one shift: pays the earnings and adds the Stress at full
         * strength, in either apartment (the High-End stress perk doesn't
         * cover work, or Overtime would be nearly free there).
         */
        public void work(Player player) {
            player.applyCashDelta(earnings);
            player.getStress().applyFullDelta(stressCost);
        }
    }
}
