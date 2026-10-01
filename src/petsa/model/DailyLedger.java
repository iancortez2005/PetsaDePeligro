package petsa.model;

import petsa.model.GameState.ApartmentType;
import petsa.model.Player.Attribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The day's money, as it happens: the balance the day started with and a
 * running total per CashCategory. GameEngine files every cash change here,
 * and at bedtime close() turns it into a DaySummary for the End of Day
 * screen.
 *
 * The other money records the screens show live here too: the Day 29
 * RentBill for the landlord's visit.
 */
public class DailyLedger {

    /** The rows of the End of Day ledger. Every cash change is filed under one of these. */
    public enum CashCategory {
        SALARY("Salary Earned"),
        EVENTS("Random Events"),
        FOOD_AND_SUPPLIES("Food & Supplies"),
        MEDICAL("Medical Bills"),
        BILLS("Rent & Utilities"),
        OTHER("Other");

        private final String label;

        CashCategory(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** One named line under Random Events, e.g. "Ambagan" / -300. */
    public static class EventEntry {
        private final String label;
        private final int amount;

        public EventEntry(String label, int amount) {
            this.label = label;
            this.amount = amount;
        }

        public String getLabel() { return label; }
        public int getAmount() { return amount; }
    }

    private final int startingBalance;
    private final Map<CashCategory, Integer> totals = new EnumMap<>(CashCategory.class);
    private final List<EventEntry> eventEntries = new ArrayList<>();

    public DailyLedger(int startingBalance) {
        this.startingBalance = startingBalance;
        for (CashCategory category : CashCategory.values()) {
            totals.put(category, 0);
        }
    }

    /** Restores a ledger saved mid-day (when loading a checkpoint). Missing categories count as 0. */
    public DailyLedger(int startingBalance, Map<CashCategory, Integer> savedTotals, List<EventEntry> savedEvents) {
        this(startingBalance);
        for (Map.Entry<CashCategory, Integer> entry : savedTotals.entrySet()) {
            totals.put(entry.getKey(), entry.getValue());
        }
        eventEntries.addAll(savedEvents);
    }

    /** A copy of today's totals, by category (for saving). */
    public Map<CashCategory, Integer> getTotals() {
        return new EnumMap<>(totals);
    }

    public int getStartingBalance() {
        return startingBalance;
    }

    /** Adds amount (positive = income, negative = expense) to the category's running total. */
    public void record(CashCategory category, int amount) {
        if (amount != 0) {
            totals.merge(category, amount, Integer::sum);
        }
    }

    /** Records a Random Events amount under the event's name, e.g. ("Ambagan", -300). It also counts toward EVENTS. */
    public void recordEvent(String label, int amount) {
        if (amount != 0) {
            record(CashCategory.EVENTS, amount);
            eventEntries.add(new EventEntry(label, amount));
        }
    }

    /** Today's named Random Events lines, in the order they happened. */
    public List<EventEntry> getEventEntries() {
        return Collections.unmodifiableList(new ArrayList<>(eventEntries));
    }

    public int getTotal(CashCategory category) {
        return totals.get(category);
    }

    /**
     * Closes the day into a DaySummary. Any gap between the tracked totals
     * and the player's actual cash is filed under OTHER, so the starting
     * balance plus every row always equals the closing balance.
     */
    public DaySummary close(int day, boolean finalDay, Player player) {
        int closingBalance = player.getCash();
        Map<CashCategory, Integer> copy = new EnumMap<>(totals);
        int tracked = 0;
        for (int amount : copy.values()) {
            tracked += amount;
        }
        int unexplained = closingBalance - startingBalance - tracked;
        if (unexplained != 0) {
            copy.merge(CashCategory.OTHER, unexplained, Integer::sum);
        }
        // Name every Random Events peso. Anything not covered by a named line (e.g. from a save made
        // before event names were recorded) is shown as one "Other events" line, so the rows still add up.
        List<EventEntry> events = new ArrayList<>(eventEntries);
        int named = 0;
        for (EventEntry entry : events) {
            named += entry.getAmount();
        }
        int unnamed = copy.get(CashCategory.EVENTS) - named;
        if (unnamed != 0) {
            events.add(new EventEntry("Other events", unnamed));
        }
        return new DaySummary(day, finalDay, startingBalance, closingBalance, copy, events, player);
    }

    // ------------------------------------------------------------------
    // DaySummary
    // ------------------------------------------------------------------

    /**
     * A finished day, frozen for the End of Day screen: the ledger (starting
     * balance, total per category, closing balance) and the player's
     * attributes and stock at the moment the day closed.
     */
    public static class DaySummary {

        /** One attribute reading, frozen at the moment the day closed. */
        public static class StatReading {
            private final String name;
            private final int value;
            private final Attribute.Zone zone;

            StatReading(Attribute attribute) {
                this.name = attribute.getName();
                this.value = attribute.getValue();
                this.zone = attribute.getZone();
            }

            public String getName() { return name; }
            public int getValue() { return value; }
            public Attribute.Zone getZone() { return zone; }
        }

        private final int day;
        private final boolean finalDay;
        private final int startingBalance;
        private final int closingBalance;
        private final Map<CashCategory, Integer> totals;
        private final int foodStock;
        private final int medicineStock;
        private final List<StatReading> stats;
        private final List<EventEntry> eventEntries;

        DaySummary(int day, boolean finalDay, int startingBalance, int closingBalance,
                Map<CashCategory, Integer> totals, List<EventEntry> eventEntries, Player player) {
            this.day = day;
            this.finalDay = finalDay;
            this.startingBalance = startingBalance;
            this.closingBalance = closingBalance;
            this.totals = Collections.unmodifiableMap(new EnumMap<>(totals));
            this.eventEntries = Collections.unmodifiableList(new ArrayList<>(eventEntries));
            this.foodStock = player.getInventory().getFoodStock();
            this.medicineStock = player.getInventory().getMedicineStock();
            List<StatReading> readings = new ArrayList<>();
            readings.add(new StatReading(player.getHunger()));
            readings.add(new StatReading(player.getStress()));
            readings.add(new StatReading(player.getAcademic()));
            readings.add(new StatReading(player.getSickness()));
            this.stats = Collections.unmodifiableList(readings);
        }

        public int getDay() { return day; }

        /** True for Day 30: the month is over and there is no next day to sleep into. */
        public boolean isFinalDay() { return finalDay; }

        public int getStartingBalance() { return startingBalance; }
        public int getClosingBalance() { return closingBalance; }
        public int getNetChange() { return closingBalance - startingBalance; }

        /** Net amount for the category (positive = money in, negative = money out, 0 = nothing happened). */
        public int getTotal(CashCategory category) {
            return totals.get(category);
        }

        /** Each Random Events amount with the event's name (e.g. "Ambagan" / -300); they sum to getTotal(EVENTS). */
        public List<EventEntry> getEventEntries() { return eventEntries; }

        public int getFoodStock() { return foodStock; }
        public int getMedicineStock() { return medicineStock; }

        /** Hunger, Stress, Academic Performance, Sickness - in that order. */
        public List<StatReading> getStats() { return stats; }
    }

    // ------------------------------------------------------------------
    // RentBill
    // ------------------------------------------------------------------

    /**
     * What the landlord charged on Day 29, itemized for the Rent Day screen.
     * The electricity and water amounts are hidden from the player until
     * this moment.
     */
    public static class RentBill {

        private final int rent;
        private final int electricity;
        private final int water;
        private final int balanceBefore;
        private final ApartmentType apartment;

        public RentBill(int rent, int electricity, int water, int balanceBefore, ApartmentType apartment) {
            this.rent = rent;
            this.electricity = electricity;
            this.water = water;
            this.balanceBefore = balanceBefore;
            this.apartment = apartment;
        }

        public int getRent() { return rent; }
        public int getElectricity() { return electricity; }
        public int getWater() { return water; }
        public int getTotal() { return rent + electricity + water; }
        public int getBalanceBefore() { return balanceBefore; }
        public int getBalanceAfter() { return balanceBefore - getTotal(); }
        /** The apartment the electricity rate came from (null if none was ever chosen). */
        public ApartmentType getApartment() { return apartment; }
    }
}
