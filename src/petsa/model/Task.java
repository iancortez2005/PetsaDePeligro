package petsa.model;

import petsa.model.Player.Inventory;

public abstract class Task {

    private final String name;

    protected Task(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract void execute(Player player, GameState state);

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
