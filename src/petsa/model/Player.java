package petsa.model;

public class Player {

    public static final int STARTING_CASH = 5000;
    public static final int MEDICAL_BILL = 500;

    private int cash;
    private final Attribute hunger;
    private final Attribute stress;
    private final Attribute academic;
    private final Attribute sickness;
    private final Inventory inventory;

    private boolean incapacitatedToday;
    private int medicalBillCount;
    private String lastMedicalBillReason;

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

    public int getCash() {
        return cash;
    }

    public void applyCashDelta(int delta) {
        cash += delta;
    }

    public void restoreCash(int cash) {
        this.cash = cash;
    }

    public Attribute getHunger() { return hunger; }
    public Attribute getStress() { return stress; }
    public Attribute getAcademic() { return academic; }
    public Attribute getSickness() { return sickness; }
    public Inventory getInventory() { return inventory; }

    public boolean isPartTimeLocked() {
        return academic.isBelowThreshold();
    }

    public boolean isIncapacitatedByStress() {
        return stress.hasCollapsed();
    }

    public void setIncapacitatedToday(boolean incapacitatedToday) {
        this.incapacitatedToday = incapacitatedToday;
    }

    public boolean isIncapacitatedToday() {
        return incapacitatedToday;
    }

    public void triggerMedicalBill(String reason) {
        applyCashDelta(-MEDICAL_BILL);
        medicalBillCount++;
        lastMedicalBillReason = reason;
    }

    public int getMedicalBillCount() {
        return medicalBillCount;
    }

    public String getLastMedicalBillReason() {
        return lastMedicalBillReason;
    }

    public void restoreMedicalBillInfo(int medicalBillCount, String lastMedicalBillReason) {
        this.medicalBillCount = medicalBillCount;
        this.lastMedicalBillReason = lastMedicalBillReason;
    }

    public void resolveSicknessEvent(int pillsNeeded) {
        boolean cured = inventory.consumeMedicine(pillsNeeded);
        if (!cured) {
            triggerMedicalBill("No medicine stocked to cure sickness");
        }
    }

    public static class Attribute {

        public enum CollapseDirection { AT_MINIMUM, AT_MAXIMUM }

        public enum Zone { GREEN, YELLOW, RED }

        private static final int MIN = 0;
        private static final int MAX = 100;

        private final String name;
        private int value;
        private double gainMultiplier = 1.0;
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

        public void applyDelta(int delta) {
            if (delta > 0 && gainMultiplier != 1.0) {
                delta = Math.max(1, (int) Math.round(delta * gainMultiplier));
            }
            value = clamp(value + delta);
        }

        public void applyFullDelta(int delta) {
            value = clamp(value + delta);
        }

        public void setGainMultiplier(double gainMultiplier) {
            this.gainMultiplier = gainMultiplier;
        }

        public void restoreValue(int value) {
            this.value = clamp(value);
        }

        public int getValue() {
            return value;
        }

        public String getName() {
            return name;
        }

        public boolean hasCollapsed() {
            if (collapseDirection == CollapseDirection.AT_MINIMUM) {
                return value <= MIN;
            }
            return value >= MAX;
        }

        public boolean isBelowThreshold() {
            if (collapseDirection == CollapseDirection.AT_MINIMUM) {
                return value <= threshold;
            }
            return value >= threshold;
        }

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

    public static class Inventory {

        public static final int MEDICINE_PRICE = 20;
        public static final int MEAL_PRICE = 70;

        private int medicineStock;
        private int foodStock;

        public int getMedicineStock() {
            return medicineStock;
        }

        public void addMedicine(int pills) {
            medicineStock += pills;
        }

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

        public void restoreStocks(int medicineStock, int foodStock) {
            this.medicineStock = medicineStock;
            this.foodStock = foodStock;
        }
    }
}
