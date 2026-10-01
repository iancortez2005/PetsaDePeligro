package petsa.model;

import petsa.model.Player.Inventory;

/**
 * Something the player does with one part of the day (Morning, Mid-day or
 * Night). Eat, Hygiene and Study are picked on the Board and can each be
 * done once a day; Walk is taken at the Door and can be repeated.
 *
 * Each kind of task is a subclass below that overrides execute(), so
 * GameEngine.performTask() can run any of them the same way (polymorphism).
 */
public abstract class Task {

    private final String name;

    protected Task(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** Applies this task's effect to the player (and the month, e.g. the water bill). */
    public abstract void execute(Player player, GameState state);

    // ------------------------------------------------------------------
    // The four tasks
    // ------------------------------------------------------------------

    /**
     * Eat: Hunger +30. Uses one day of bulk food from the shelf if there is
     * any (from the Discount event or the Shop); only costs P70 when the
     * shelf is empty.
     */
    public static class EatTask extends Task {

        public static final int MEAL_COST = Inventory.MEAL_PRICE;
        private static final int HUNGER_GAIN = 30;

        public EatTask() {
            super("Eat");
        }

        @Override
        public void execute(Player player, GameState state) {
            boolean usedStock = player.getInventory().consumeFoodStock(1);
            if (!usedStock) {
                player.applyCashDelta(-MEAL_COST);
            }
            player.getHunger().applyDelta(HUNGER_GAIN);
        }
    }

    /**
     * Hygiene (a shower): Sickness -15, and P20 added to the water bill paid
     * on Day 29. On a rainy day the Board offers Rainy Day Laundry instead
     * (see RandomEvent.RainyDayLaundryEvent).
     */
    public static class HygieneTask extends Task {

        private static final int WATER_COST = 20;
        private static final int SICKNESS_REDUCTION = 15;

        public HygieneTask() {
            super("Hygiene");
        }

        @Override
        public void execute(Player player, GameState state) {
            state.addWaterUsage(WATER_COST);
            player.getSickness().applyDelta(-SICKNESS_REDUCTION);
        }
    }

    /** Study: free. Academics +5, and Stress +5 - studying every day adds up, so plan some walks. */
    public static class StudyTask extends Task {

        private static final int ACADEMIC_GAIN = 5;
        private static final int STRESS_COST = 5;

        public StudyTask() {
            super("Study");
        }

        @Override
        public void execute(Player player, GameState state) {
            player.getAcademic().applyDelta(ACADEMIC_GAIN);
            player.getStress().applyDelta(STRESS_COST);
        }
    }

    /**
     * Walk: going outside to de-stress, Stress -10. Available at the Door in
     * any part of any day, and still possible at 100% Stress (it's the only
     * way down). In the rain, going out makes the player sick - see
     * GameEngine.goOut().
     */
    public static class WalkTask extends Task {

        private static final int STRESS_RELIEF = 10;

        public WalkTask() {
            super("Walk");
        }

        @Override
        public void execute(Player player, GameState state) {
            player.getStress().applyDelta(-STRESS_RELIEF);
        }
    }
}
