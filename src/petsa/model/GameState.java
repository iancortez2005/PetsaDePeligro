package petsa.model;

import petsa.model.Timeline.TimeSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class GameState {

    public static final int RENT_DUE_DAY = 29;
    public static final int RENT_AMOUNT = 1500;
    public static final int TRANSPORT_DUE_DAY = 30;
    public static final int TRANSPORT_AMOUNT = 500;

    public enum ApartmentType {
        STANDARD(300, 1.0),
        HIGH_END(700, 0.6);

        private final int fixedElectricity;
        private final double stressGainMultiplier;

        ApartmentType(int fixedElectricity, double stressGainMultiplier) {
            this.fixedElectricity = fixedElectricity;
            this.stressGainMultiplier = stressGainMultiplier;
        }

        public int getFixedElectricity() {
            return fixedElectricity;
        }

        public double getStressGainMultiplier() {
            return stressGainMultiplier;
        }
    }

    public enum Weather {
        CLEAR,
        RAINY
    }

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

    private int day;
    private TimeSlot timeSlot;
    private final Player player;
    private ApartmentType apartment;
    private Weather todayWeather;
    private boolean phoneBroken;
    private boolean monthCompleted;
    private int accumulatedWaterBill;
    private final List<PendingEffect> pendingEffects;
    private final Set<Integer> rainyDays;
    private final List<String> eventHistory;

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

    public Set<Integer> getRainyDays() { return new TreeSet<>(rainyDays); }

    public void setRainyDays(Set<Integer> days) {
        rainyDays.clear();
        rainyDays.addAll(days);
    }

    public boolean isRainyDay(int day) { return rainyDays.contains(day); }

    public List<String> getEventHistory() { return new ArrayList<>(eventHistory); }

    public void recordEvent(String eventName) { eventHistory.add(eventName); }

    public int timesEventHappened(String eventName) {
        int count = 0;
        for (String name : eventHistory) {
            if (name.equals(eventName)) {
                count++;
            }
        }
        return count;
    }

    public String getLastEventName() {
        return eventHistory.isEmpty() ? null : eventHistory.get(eventHistory.size() - 1);
    }

    public void restoreEventHistory(List<String> history) {
        eventHistory.clear();
        eventHistory.addAll(history);
    }

    public boolean isPhoneBroken() { return phoneBroken; }
    public void setPhoneBroken(boolean phoneBroken) { this.phoneBroken = phoneBroken; }

    public ApartmentType getApartment() { return apartment; }

    public void chooseApartment(ApartmentType apartment) {
        this.apartment = apartment;
        if (apartment != null) {
            player.getStress().setGainMultiplier(apartment.getStressGainMultiplier());
        }
    }

    public int getAccumulatedWaterBill() {
        return accumulatedWaterBill;
    }

    public void addWaterUsage(int amount) {
        accumulatedWaterBill += amount;
    }

    public void schedulePendingEffect(PendingEffect effect) {
        pendingEffects.add(effect);
    }

    public List<PendingEffect> getPendingEffects() {
        return new ArrayList<>(pendingEffects);
    }

    public void restoreDayAndSlot(int day, TimeSlot timeSlot) {
        this.day = day;
        this.timeSlot = timeSlot;
    }

    public void restoreWaterBill(int accumulatedWaterBill) {
        this.accumulatedWaterBill = accumulatedWaterBill;
    }

    public void restorePendingEffects(List<PendingEffect> effects) {
        pendingEffects.clear();
        pendingEffects.addAll(effects);
    }

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

    public void applyEndOfMonthBills() {
        int electricity = getElectricityBill();
        player.applyCashDelta(-RENT_AMOUNT);
        player.applyCashDelta(-electricity);
        player.applyCashDelta(-accumulatedWaterBill);
    }

    public void applyTransportCost() {
        player.applyCashDelta(-TRANSPORT_AMOUNT);
    }

    public boolean isGameOver() {
        if (monthCompleted) {
            return false;
        }
        return player.getCash() <= 0 || day >= TRANSPORT_DUE_DAY;
    }

    public boolean isMonthCompleted() { return monthCompleted; }

    public void setMonthCompleted(boolean monthCompleted) { this.monthCompleted = monthCompleted; }

    public int getElectricityBill() {
        return (apartment != null) ? apartment.getFixedElectricity() : ApartmentType.STANDARD.getFixedElectricity();
    }
}
