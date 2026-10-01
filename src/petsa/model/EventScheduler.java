package petsa.model;

import petsa.model.GameState.Weather;
import petsa.model.RandomEvent.AcademicCommissionEvent;
import petsa.model.RandomEvent.AmbaganEvent;
import petsa.model.RandomEvent.BrokenPhoneEvent;
import petsa.model.RandomEvent.BrokenSchoolEquipmentEvent;
import petsa.model.RandomEvent.DiscountEvent;
import petsa.model.RandomEvent.SocialEvent;
import petsa.model.RandomEvent.UtangEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public class EventScheduler {

    public static final int RAINY_DAYS_PER_MONTH = 10;
    private static final int FIRST_RAINY_DAY = 2;
    private static final int LAST_RAINY_DAY = GameState.RENT_DUE_DAY;

    public static final int SICKNESS_GAIN_IN_RAIN = 30;
    private static final int STAYING_IN_RISK_PERCENT = 50;
    private static final int MIN_PILLS_FOR_A_COLD = 1;
    private static final int MAX_PILLS_FOR_A_COLD = 3;

    private final List<RandomEvent> pool;
    private final Random random;

    public EventScheduler() {
        this(new Random());
    }

    EventScheduler(Random random) {
        this.random = random;
        this.pool = buildPool();
    }

    private List<RandomEvent> buildPool() {
        List<RandomEvent> events = new ArrayList<>();
        events.add(new SocialEvent());
        events.add(new DiscountEvent());
        events.add(new AmbaganEvent());
        events.add(new UtangEvent(random));
        events.add(new BrokenPhoneEvent());
        events.add(new BrokenSchoolEquipmentEvent());
        events.add(new AcademicCommissionEvent());
        return events;
    }

    public RandomEvent pickEventFor(GameState state) {
        List<RandomEvent> candidates = new ArrayList<>();
        for (RandomEvent event : pool) {
            boolean usedUp = !event.isRepeatable() && state.timesEventHappened(event.getName()) > 0;
            if (event.isEligible(state) && !usedUp) {
                candidates.add(event);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No eligible random event for day " + state.getDay());
        }

        String last = state.getLastEventName();
        if (candidates.size() > 1) {
            candidates.removeIf(event -> event.getName().equals(last));
        }

        int fewest = Integer.MAX_VALUE;
        for (RandomEvent event : candidates) {
            fewest = Math.min(fewest, state.timesEventHappened(event.getName()));
        }
        List<RandomEvent> freshest = new ArrayList<>();
        for (RandomEvent event : candidates) {
            if (state.timesEventHappened(event.getName()) == fewest) {
                freshest.add(event);
            }
        }

        RandomEvent picked = freshest.get(random.nextInt(freshest.size()));
        state.recordEvent(picked.getName());
        return picked;
    }

    public Set<Integer> planRainyDays() {
        List<Integer> days = new ArrayList<>();
        for (int day = FIRST_RAINY_DAY; day <= LAST_RAINY_DAY; day++) {
            days.add(day);
        }
        Collections.shuffle(days, random);
        return new TreeSet<>(days.subList(0, RAINY_DAYS_PER_MONTH));
    }

    public boolean applyWeatherFor(GameState state) {
        if (state.isRainyDay(state.getDay())) {
            state.setTodayWeather(Weather.RAINY);
            return true;
        }
        state.setTodayWeather(Weather.CLEAR);
        return false;
    }

    public void goOutInRain(Player player, boolean firstOutingToday) {
        player.getSickness().applyDelta(SICKNESS_GAIN_IN_RAIN);
        if (firstOutingToday) {
            catchCold(player);
        }
    }

    public void rollStayedInRisk(Player player) {
        if (random.nextInt(100) < STAYING_IN_RISK_PERCENT) {
            player.getSickness().applyDelta(SICKNESS_GAIN_IN_RAIN);
            catchCold(player);
        }
    }

    private void catchCold(Player player) {
        int pillsNeeded = MIN_PILLS_FOR_A_COLD + random.nextInt(MAX_PILLS_FOR_A_COLD - MIN_PILLS_FOR_A_COLD + 1);
        player.resolveSicknessEvent(pillsNeeded);
    }
}
