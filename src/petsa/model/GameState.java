package petsa.model;

import petsa.model.Timeline.TimeSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Everything about one month in progress: the current day and time slot,
 * the player, the chosen apartment, today's weather and the month's weather
 * plan, the water bill so far, the random events delivered so far, and any
 * delayed effects (such as the Day 28 Utang repayment).
 *
 * The small types that only describe the month live here too: the two
 * apartments, the two kinds of weather, and a delayed effect.
 */
public class GameState {

    public static final int RENT_DUE_DAY = 29;
    public static final int RENT_AMOUNT = 1500;
    public static final int TRANSPORT_DUE_DAY = 30;
    public static final int TRANSPORT_AMOUNT = 500;

    /**
     * The two apartments, chosen before Day 1. Electricity is a fixed rate
     * charged (and first revealed) on Day 29; the stress multiplier scales
     * every Stress increase for the whole month, except part-time shifts
     * (see PartTimeJob.ShiftType.work).
     */
    public enum ApartmentType {
        STANDARD(300, 1.0),
        HIGH_END(700, 0.6); // Stress climbs 40% slower (not at work), but electricity costs P400 more

        private final int fixedElectricity;
        private final double stressGainMultiplier;

        ApartmentType(int fixedElectricity, double stressGainMultiplier) {
            this.fixedElectricity = fixedElectricity;
            this.stressGainMultiplier = stressGainMultiplier;
        }

        public int getFixedElectricity() {
            return fixedElectricity;
        }

        /** Multiplier applied to Stress increases; HIGH_END reduces how much Stress climbs. */
        public double getStressGainMultiplier() {
            return stressGainMultiplier;
        }
    }

    /**
     * Today's weather. On a RAINY day going outside makes the player sick,
     * staying in risks a cold, and Hygiene becomes Rainy Day Laundry.
     */
    public enum Weather {
        CLEAR,
        RAINY
    }

    /**
     * A cash change scheduled for a later day and time slot rather than now -
     * e.g. the Utang repayment, which always lands on the morning of Day 28.
     * GameState resolves these as the calendar reaches them.
     */
    public static class PendingEffect {

        private final int triggerDay;
        private final TimeSlot triggerSlot;
        private final String description;
        private final int cashDelta;

        public PendingEffect(int triggerDay, TimeSlot triggerSlot, String description, int cashDelta) {
            this.triggerDay = triggerDay;
            this.triggerSlot = triggerSlot;
            this.description = description;
            this.cashDelta = cashDelta;
        }

        public boolean isDue(int currentDay, TimeSlot currentSlot) {
            return currentDay == triggerDay && currentSlot == triggerSlot;
        }

        public int getCashDelta() {
            return cashDelta;
        }

        public String getDescription() {
            return description;
        }

        public int getTriggerDay() {
            return triggerDay;
        }

        public TimeSlot getTriggerSlot() {
            return triggerSlot;
        }
    }

    private int day; // 1-30
    private TimeSlot timeSlot;
    private final Player player;
    private ApartmentType apartment; // chosen before Day 1
    private Weather todayWeather;
    private boolean phoneBroken; // cracked screen after declining the Broken Phone repair; fixed by a later repair
    private boolean monthCompleted; // the Day 30 fare home was paid - the player has won
    private int accumulatedWaterBill; // grows with every shower, charged on Day 29
    private final List<PendingEffect> pendingEffects;
    private final Set<Integer> rainyDays;     // the month's weather plan, drawn once per game (see EventScheduler.planRainyDays)
    private final List<String> eventHistory;  // names of the random events delivered so far, oldest first

    public GameState() {
        this.day = 1;
        this.timeSlot = TimeSlot.MORNING;
        this.player = new Player();
        this.apartment = null;
        this.todayWeather = Weather.CLEAR;
        this.accumulatedWaterBill = 0;
        this.pendingEffects = new ArrayList<>();
        this.rainyDays = new TreeSet<>();
        this.eventHistory = new ArrayList<>();
    }

    public int getDay() { return day; }
    public TimeSlot getTimeSlot() { return timeSlot; }
    public Player getPlayer() { return player; }
    public Weather getTodayWeather() { return todayWeather; }
    public void setTodayWeather(Weather weather) { this.todayWeather = weather; }

    // --- The month's weather plan ---

    /** The days of this month that are rainy (a copy, sorted). */
    public Set<Integer> getRainyDays() { return new TreeSet<>(rainyDays); }

    /** Replaces the month's weather plan. Used when a game starts and when a checkpoint is loaded. */
    public void setRainyDays(Set<Integer> days) {
        rainyDays.clear();
        rainyDays.addAll(days);
    }

    /** True if the given day is one of this month's rainy days. */
    public boolean isRainyDay(int day) { return rainyDays.contains(day); }

    // --- Random event history ---

    /** Names of every random event delivered so far this month, oldest first (a copy). */
    public List<String> getEventHistory() { return new ArrayList<>(eventHistory); }

    public void recordEvent(String eventName) { eventHistory.add(eventName); }

    /** How many times the named event has been delivered this month. */
    public int timesEventHappened(String eventName) {
        int count = 0;
        for (String name : eventHistory) {
            if (name.equals(eventName)) {
                count++;
            }
        }
        return count;
    }

    /** The most recently delivered event's name, or null if none has happened yet. */
    public String getLastEventName() {
        return eventHistory.isEmpty() ? null : eventHistory.get(eventHistory.size() - 1);
    }

    /** Restore-only: replaces the event history wholesale. Used when loading a checkpoint. */
    public void restoreEventHistory(List<String> history) {
        eventHistory.clear();
        eventHistory.addAll(history);
    }

    // --- Phone and apartment ---

    /** True while the phone's screen is cracked (the player declined the Broken Phone repair). */
    public boolean isPhoneBroken() { return phoneBroken; }
    public void setPhoneBroken(boolean phoneBroken) { this.phoneBroken = phoneBroken; }

    public ApartmentType getApartment() { return apartment; }

    /**
     * Sets the apartment and applies its effect on Stress: every later
     * Stress increase (except from a part-time shift) is scaled by the
     * apartment's stress-gain multiplier
     * (1.0 for Standard, lower for High-End). Its electricity rate is
     * charged on Day 29 (see applyEndOfMonthBills).
     */
    public void chooseApartment(ApartmentType apartment) {
        this.apartment = apartment;
        if (apartment != null) {
            player.getStress().setGainMultiplier(apartment.getStressGainMultiplier());
        }
    }

    // --- Bills and delayed effects ---

    public int getAccumulatedWaterBill() {
        return accumulatedWaterBill;
    }

    /** Adds to the water bill (e.g. from a shower). */
    public void addWaterUsage(int amount) {
        accumulatedWaterBill += amount;
    }

    /** Registers a delayed effect (e.g. the Utang repayment, always Day 28 morning). */
    public void schedulePendingEffect(PendingEffect effect) {
        pendingEffects.add(effect);
    }

    /** A copy of the effects still waiting to happen. Saved with every checkpoint. */
    public List<PendingEffect> getPendingEffects() {
        return new ArrayList<>(pendingEffects);
    }

    /** Restore-only: directly sets day/slot, bypassing normal advancement. Used when loading a checkpoint. */
    public void restoreDayAndSlot(int day, TimeSlot timeSlot) {
        this.day = day;
        this.timeSlot = timeSlot;
    }

    /** Restore-only: directly sets the water bill. Used when loading a checkpoint. */
    public void restoreWaterBill(int accumulatedWaterBill) {
        this.accumulatedWaterBill = accumulatedWaterBill;
    }

    /** Restore-only: replaces the pending-effects queue wholesale. Used when loading a checkpoint. */
    public void restorePendingEffects(List<PendingEffect> effects) {
        pendingEffects.clear();
        pendingEffects.addAll(effects);
    }

    /**
     * Resolves and removes any pending effects due at the current day/slot.
     * Called automatically every time the slot advances.
     */
    public List<PendingEffect> resolveDuePendingEffects() {
        List<PendingEffect> due = new ArrayList<>();
        List<PendingEffect> remaining = new ArrayList<>();
        for (PendingEffect effect : pendingEffects) {
            if (effect.isDue(day, timeSlot)) {
                due.add(effect);
                player.applyCashDelta(effect.getCashDelta());
            } else {
                remaining.add(effect);
            }
        }
        pendingEffects.clear();
        pendingEffects.addAll(remaining);
        return due;
    }

    /**
     * Advances to the next time slot, rolling over to the next day after
     * NIGHT. Resets the daily incapacitation flag on day rollover, then
     * resolves any pending effects due at the new day/slot.
     */
    public void advanceTimeSlot() {
        TimeSlot next = timeSlot.next();
        if (next == null) {
            day++;
            timeSlot = TimeSlot.MORNING;
            player.setIncapacitatedToday(false);
        } else {
            timeSlot = next;
        }
        resolveDuePendingEffects();
    }

    /** Applies the Day 29 rent + utility bills (electricity fixed by apartment, water from the month's showers). */
    public void applyEndOfMonthBills() {
        int electricity = getElectricityBill();
        player.applyCashDelta(-RENT_AMOUNT);
        player.applyCashDelta(-electricity);
        player.applyCashDelta(-accumulatedWaterBill);
    }

    /** Applies the Day 30 fare home. */
    public void applyTransportCost() {
        player.applyCashDelta(-TRANSPORT_AMOUNT);
    }

    /**
     * True once the player has lost: their cash is at zero or below (broke),
     * or it's Day 30 and they couldn't pay the fare home (stranded). Never
     * true after the fare is paid - ending the month with exactly P0 left is
     * still a win.
     */
    public boolean isGameOver() {
        if (monthCompleted) {
            return false;
        }
        return player.getCash() <= 0 || day >= TRANSPORT_DUE_DAY;
    }

    /** True once the Day 30 fare home has been paid: the month is won. */
    public boolean isMonthCompleted() { return monthCompleted; }

    public void setMonthCompleted(boolean monthCompleted) { this.monthCompleted = monthCompleted; }

    /** The electricity charged on Day 29, from the chosen apartment (Standard if none was chosen). */
    public int getElectricityBill() {
        return (apartment != null) ? apartment.getFixedElectricity() : ApartmentType.STANDARD.getFixedElectricity();
    }
}
