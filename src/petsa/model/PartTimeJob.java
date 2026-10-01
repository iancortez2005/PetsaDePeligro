package petsa.model;

import java.util.Arrays;
import java.util.List;

public final class PartTimeJob {

    public static final List<Integer> AVAILABLE_DAYS = Arrays.asList(7, 14, 21, 24, 28);

    private PartTimeJob() {
    }

    public static boolean isAvailableOn(int day) {
        return AVAILABLE_DAYS.contains(day);
    }

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

        public void work(Player player) {
            player.applyCashDelta(earnings);
            player.getStress().applyDelta(stressCost);
        }
    }
}
