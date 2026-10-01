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

/**
 * The month's luck, all from one Random: which event arrives on each event
 * day, which 10 days are rainy, and what the rain does to the player.
 *
 * Events: the pool holds the seven drawn events. Only eligible ones are
 * considered (see RandomEvent.isEligible), and the draw remembers what
 * already happened this month (see pickEventFor). Rainy Day Laundry and
 * Pickpocketed are never drawn - GameEngine triggers them directly.
 *
 * Weather: the rainy days are drawn once when a game starts (see
 * planRainyDays) and saved with every checkpoint. Rain can land on any
 * day, including an event day.
 */
public class EventScheduler {

    /** How many days of the month are rainy (the other days are sunny). */
    public static final int RAINY_DAYS_PER_MONTH = 10;
    /**
     * The days rain can fall on. Day 1 is left out because the tutorial walks
     * the player through a normal shower, and Day 30 has no gameplay - so all
     * 10 rainy days land on days the player actually plays.
     */
    private static final int FIRST_RAINY_DAY = 2;
    private static final int LAST_RAINY_DAY = GameState.RENT_DUE_DAY;

    /** Sickness added each time the player goes out in the rain, and when a rainy day indoors ends in a cold. */
    public static final int SICKNESS_GAIN_IN_RAIN = 30;
    private static final int STAYING_IN_RISK_PERCENT = 50;
    private static final int MIN_PILLS_FOR_A_COLD = 1;
    private static final int MAX_PILLS_FOR_A_COLD = 3;

    private final List<RandomEvent> pool;
    private final Random random;

    public EventScheduler() {
        this(new Random());
    }

    /** Package-visible so a test can pass a seeded Random. */
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

    // ------------------------------------------------------------------
    // Random events
    // ------------------------------------------------------------------

    /**
     * Picks one event for today and records it in the state's event history.
     * The draw remembers what already happened this month, so the same
     * event can't keep coming back:
     *  - one-time events (RandomEvent.isRepeatable() == false, e.g. the
     *    Academic Commission) never happen twice in a month;
     *  - events that have happened the fewest times go first, so every
     *    event gets its turn before any repeats;
     *  - the same event never happens on two event days in a row, if
     *    there's anything else to pick.
     * Throws IllegalStateException if nothing in the pool is currently
     * eligible - shouldn't happen, since Ambagan is always available.
     */
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

        // Not the same event as last time, unless it's the only option left.
        String last = state.getLastEventName();
        if (candidates.size() > 1) {
            candidates.removeIf(event -> event.getName().equals(last));
        }

        // Only the events that have happened the fewest times so far.
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

    // ------------------------------------------------------------------
    // Weather
    // ------------------------------------------------------------------

    /**
     * Draws this month's weather: exactly RAINY_DAYS_PER_MONTH random days
     * between FIRST_RAINY_DAY and LAST_RAINY_DAY are rainy, the rest sunny.
     * Called once when a new game starts; the plan is saved with every
     * checkpoint so loading a game keeps the same weather.
     */
    public Set<Integer> planRainyDays() {
        List<Integer> days = new ArrayList<>();
        for (int day = FIRST_RAINY_DAY; day <= LAST_RAINY_DAY; day++) {
            days.add(day);
        }
        Collections.shuffle(days, random);
        return new TreeSet<>(days.subList(0, RAINY_DAYS_PER_MONTH));
    }

    /** Sets today's weather from the month's plan. Returns true if today is rainy. */
    public boolean applyWeatherFor(GameState state) {
        if (state.isRainyDay(state.getDay())) {
            state.setTodayWeather(Weather.RAINY);
            return true;
        }
        state.setTodayWeather(Weather.CLEAR);
        return false;
    }

    /**
     * The player goes outside in the rain (a walk, a hangout, a work shift):
     * +30 Sickness every time, and the first outing of the day also catches
     * a cold.
     */
    public void goOutInRain(Player player, boolean firstOutingToday) {
        player.getSickness().applyDelta(SICKNESS_GAIN_IN_RAIN);
        if (firstOutingToday) {
            catchCold(player);
        }
    }

    /** End of a rainy day spent indoors: a 50% chance of catching a cold anyway (+30 Sickness and the cold). */
    public void rollStayedInRisk(Player player) {
        if (random.nextInt(100) < STAYING_IN_RISK_PERCENT) {
            player.getSickness().applyDelta(SICKNESS_GAIN_IN_RAIN);
            catchCold(player);
        }
    }

    /** A cold uses 1-3 medicine pills, or a Medical Bill if the shelf doesn't have enough. */
    private void catchCold(Player player) {
        int pillsNeeded = MIN_PILLS_FOR_A_COLD + random.nextInt(MAX_PILLS_FOR_A_COLD - MIN_PILLS_FOR_A_COLD + 1);
        player.resolveSicknessEvent(pillsNeeded);
    }
}
