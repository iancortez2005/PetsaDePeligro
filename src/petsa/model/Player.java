package petsa.model;

/**
 * The player: their cash, the four attributes and the shelf's stock of
 * food and medicine. Each attribute has one danger point:
 *   - Hunger    at 0%            -> collapse, P500 Medical Bill
 *   - Stress    at 100%          -> can't Eat, Hygiene or Study until a Walk (or a hangout) brings it down
 *   - Academics at 65% or lower  -> the part-time job is locked
 *   - Sickness  at 100%          -> falls ill: 1-3 pills, or a P500 Medical Bill if not enough
 */
public class Player {

    public static final int STARTING_CASH = 5000;
    public static final int MEDICAL_BILL = 500;

    private int cash;
    private final Attribute hunger;
    private final Attribute stress;
    private final Attribute academic;
    private final Attribute sickness;
    private final Inventory inventory;

    private boolean incapacitatedToday;   // set when a task fails at 100% Stress; cleared at day rollover
    private int medicalBillCount;         // how many Medical Bills this month
    private String lastMedicalBillReason; // why the most recent one was charged

    public Player() {
        this.cash = STARTING_CASH;
        this.hunger = new Attribute("Hunger", 100, Attribute.CollapseDirection.AT_MINIMUM, 30);
        this.stress = new Attribute("Stress", 0, Attribute.CollapseDirection.AT_MAXIMUM, 100);
        this.academic = new Attribute("Academic Performance", 75, Attribute.CollapseDirection.AT_MINIMUM, 65);
        this.sickness = new Attribute("Sickness", 0, Attribute.CollapseDirection.AT_MAXIMUM, 100);
        this.inventory = new Inventory();
        this.incapacitatedToday = false;
        this.medicalBillCount = 0;
        this.lastMedicalBillReason = null;
    }

    // --- Cash ---

    public int getCash() {
        return cash;
    }

    /** Applies a cash change (positive = income, negative = expense). Allowed to go negative; GameState checks for game over. */
    public void applyCashDelta(int delta) {
        cash += delta;
    }

    /** Restore-only: directly sets cash. Used when loading a checkpoint. */
    public void restoreCash(int cash) {
        this.cash = cash;
    }

    // --- Attributes and stock ---

    public Attribute getHunger() { return hunger; }
    public Attribute getStress() { return stress; }
    public Attribute getAcademic() { return academic; }
    public Attribute getSickness() { return sickness; }
    public Inventory getInventory() { return inventory; }

    /** True once Academic Performance is at/below 65 - the part-time job won't take the player. */
    public boolean isPartTimeLocked() {
        return academic.isBelowThreshold();
    }

    /** True once Stress has hit 100 - Eat, Hygiene and Study fail until it comes down. */
    public boolean isIncapacitatedByStress() {
        return stress.hasCollapsed();
    }

    public void setIncapacitatedToday(boolean incapacitatedToday) {
        this.incapacitatedToday = incapacitatedToday;
    }

    public boolean isIncapacitatedToday() {
        return incapacitatedToday;
    }

    // --- Medical bills ---

    /** Charges a P500 Medical Bill (a Hunger collapse, or getting sick without enough medicine) and records why. */
    public void triggerMedicalBill(String reason) {
        applyCashDelta(-MEDICAL_BILL);
        medicalBillCount++;
        lastMedicalBillReason = reason;
    }

    public int getMedicalBillCount() {
        return medicalBillCount;
    }

    /** Why the most recent Medical Bill was charged, or null if there hasn't been one. */
    public String getLastMedicalBillReason() {
        return lastMedicalBillReason;
    }

    /** Restore-only: directly sets the Medical Bill count/reason. Used when loading a checkpoint. */
    public void restoreMedicalBillInfo(int medicalBillCount, String lastMedicalBillReason) {
        this.medicalBillCount = medicalBillCount;
        this.lastMedicalBillReason = lastMedicalBillReason;
    }

    /** The player gets sick: uses pillsNeeded medicine pills if the shelf has them all, otherwise a Medical Bill. */
    public void resolveSicknessEvent(int pillsNeeded) {
        boolean cured = inventory.consumeMedicine(pillsNeeded);
        if (!cured) {
            triggerMedicalBill("No medicine stocked to cure sickness");
        }
    }

    // ------------------------------------------------------------------
    // Attribute
    // ------------------------------------------------------------------

    /**
     * One of the four meters (Hunger, Stress, Academic Performance,
     * Sickness), always a whole percentage from 0 to 100.
     *
     * Each has a "collapse" direction - whether hitting 0 (Hunger) or 100
     * (Stress, Sickness) is the bad outcome - and a threshold for a second
     * rule (Academics at/below 65 locks the part-time job).
     */
    public static class Attribute {

        public enum CollapseDirection { AT_MINIMUM, AT_MAXIMUM }

        /** Green / yellow / red colouring for the HUD and the End of Day screen. */
        public enum Zone { GREEN, YELLOW, RED }

        private static final int MIN = 0;
        private static final int MAX = 100;

        private final String name;
        private int value;
        private double gainMultiplier = 1.0; // scales increases only (see setGainMultiplier)
        private final CollapseDirection collapseDirection;
        private final int threshold;

        public Attribute(String name, int startingValue, CollapseDirection collapseDirection, int threshold) {
            this.name = name;
            this.value = clamp(startingValue);
            this.collapseDirection = collapseDirection;
            this.threshold = threshold;
        }

        private int clamp(int v) {
            return Math.max(MIN, Math.min(MAX, v));
        }

        /** Applies a positive or negative change, kept within 0-100. */
        public void applyDelta(int delta) {
            if (delta > 0 && gainMultiplier != 1.0) {
                delta = Math.max(1, (int) Math.round(delta * gainMultiplier));
            }
            value = clamp(value + delta);
        }

        /**
         * Scales every INCREASE to this attribute (decreases are untouched).
         * The High-End apartment uses this so Stress builds up more slowly:
         * 0.6 turns a +40 shift into +24.
         */
        public void setGainMultiplier(double gainMultiplier) {
            this.gainMultiplier = gainMultiplier;
        }

        /** Restore-only: directly sets the value (kept within 0-100). Used when loading a checkpoint. */
        public void restoreValue(int value) {
            this.value = clamp(value);
        }

        public int getValue() {
            return value;
        }

        public String getName() {
            return name;
        }

        /** True once the attribute has hit its bad end (0 for Hunger, 100 for Stress and Sickness). */
        public boolean hasCollapsed() {
            if (collapseDirection == CollapseDirection.AT_MINIMUM) {
                return value <= MIN;
            }
            return value >= MAX;
        }

        /** True once the attribute has crossed its threshold, in the same direction as its collapse. */
        public boolean isBelowThreshold() {
            if (collapseDirection == CollapseDirection.AT_MINIMUM) {
                return value <= threshold;
            }
            return value >= threshold;
        }

        /** Green more than 60 points from danger, yellow 26-60, red 25 or less. */
        public Zone getZone() {
            int safeDistance = (collapseDirection == CollapseDirection.AT_MINIMUM) ? value : (MAX - value);
            if (safeDistance > 60) return Zone.GREEN;
            if (safeDistance > 25) return Zone.YELLOW;
            return Zone.RED;
        }

        @Override
        public String toString() {
            return name + ": " + value + "%";
        }
    }

    // ------------------------------------------------------------------
    // Inventory
    // ------------------------------------------------------------------

    /**
     * The shelf: medicine pills and days of bulk food. Getting sick needs the
     * full 1-3 pills at once - even one short means a Medical Bill instead.
     */
    public static class Inventory {

        public static final int MEDICINE_PRICE = 20; // per pill
        public static final int MEAL_PRICE = 70;     // per day of food

        private int medicineStock;
        private int foodStock;

        public int getMedicineStock() {
            return medicineStock;
        }

        public void addMedicine(int pills) {
            medicineStock += pills;
        }

        /** Uses exactly pillsNeeded pills. Returns false (and uses none) if the shelf doesn't have them all. */
        public boolean consumeMedicine(int pillsNeeded) {
            if (medicineStock < pillsNeeded) {
                return false;
            }
            medicineStock -= pillsNeeded;
            return true;
        }

        public int getFoodStock() {
            return foodStock;
        }

        public void addFoodStock(int units) {
            foodStock += units;
        }

        public boolean consumeFoodStock(int units) {
            if (foodStock < units) {
                return false;
            }
            foodStock -= units;
            return true;
        }

        /** Restore-only: directly sets both stocks. Used when loading a checkpoint. */
        public void restoreStocks(int medicineStock, int foodStock) {
            this.medicineStock = medicineStock;
            this.foodStock = foodStock;
        }
    }
}
