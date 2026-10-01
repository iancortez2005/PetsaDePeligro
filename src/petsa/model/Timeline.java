package petsa.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The month's fixed calendar: which kind of day each date is (Standard,
 * Random Event, Part-Time, ...), and the three time slots every day is
 * split into. The kind of day never changes - only which event fills an
 * event day, and which days rain, are random (see EventScheduler). The
 * number of event days per week already builds from 1 in Week 1 to 3 in
 * Week 4.
 */
public final class Timeline {

    /** The kind of gameplay day for a date. */
    public enum DayType {
        STANDARD_TUTORIAL,     // Day 1 - the guided Hygiene / Eat / Study day
        STANDARD,              // the player picks any task in each slot
        RANDOM_EVENT,          // one event arrives on the phone (see EventScheduler)
        PART_TIME_AVAILABLE,   // the Door offers a part-time shift at Night
        RENT_AND_UTILITIES,    // Day 29 - the landlord collects rent and bills
        TRANSPORT_AND_ENDING   // Day 30 - the fare home, then the ending
    }

    /** The three parts of every day. The player does one thing in each. */
    public enum TimeSlot {
        MORNING("Morning"),
        AFTERNOON("Mid-day"),
        NIGHT("Night");

        private final String label;

        TimeSlot(String label) {
            this.label = label;
        }

        /** The name the player sees: "Morning", "Mid-day" or "Night". */
        public String getLabel() {
            return label;
        }

        /** The slot after this one, or null after NIGHT (GameState then moves on to the next day's MORNING). */
        public TimeSlot next() {
            switch (this) {
                case MORNING:   return AFTERNOON;
                case AFTERNOON: return NIGHT;
                default:        return null;
            }
        }
    }

    private static final Map<Integer, DayType> SCHEDULE = buildSchedule();

    /** The end of each week - the days the phone reminds the player how long they have until rent is due. */
    public static final List<Integer> RENT_REMINDER_DAYS = Collections.unmodifiableList(Arrays.asList(7, 14, 21, 28));

    private Timeline() {
        // static utility class - not instantiable
    }

    public static boolean isRentReminderDay(int day) {
        return RENT_REMINDER_DAYS.contains(day);
    }

    public static DayType getDayType(int day) {
        DayType type = SCHEDULE.get(day);
        if (type == null) {
            throw new IllegalArgumentException("No timeline entry for day " + day);
        }
        return type;
    }

    private static Map<Integer, DayType> buildSchedule() {
        Map<Integer, DayType> schedule = new HashMap<>();

        // Week 1: Learning the Ropes
        schedule.put(1, DayType.STANDARD_TUTORIAL); // the apartment is chosen just before it starts
        schedule.put(2, DayType.STANDARD);
        schedule.put(3, DayType.STANDARD);
        schedule.put(4, DayType.RANDOM_EVENT);
        schedule.put(5, DayType.STANDARD);
        schedule.put(6, DayType.STANDARD);
        schedule.put(7, DayType.PART_TIME_AVAILABLE);

        // Week 2: The Pressure Starts
        schedule.put(8, DayType.STANDARD);
        schedule.put(9, DayType.RANDOM_EVENT);
        schedule.put(10, DayType.STANDARD);
        schedule.put(11, DayType.STANDARD);
        schedule.put(12, DayType.RANDOM_EVENT);
        schedule.put(13, DayType.STANDARD);
        schedule.put(14, DayType.PART_TIME_AVAILABLE);

        // Week 3: Petsa de Peligro Begins
        schedule.put(15, DayType.STANDARD);
        schedule.put(16, DayType.RANDOM_EVENT);
        schedule.put(17, DayType.STANDARD);
        schedule.put(18, DayType.RANDOM_EVENT);
        schedule.put(19, DayType.STANDARD);
        schedule.put(20, DayType.RANDOM_EVENT);
        schedule.put(21, DayType.PART_TIME_AVAILABLE);

        // Week 4: Survival Mode
        schedule.put(22, DayType.STANDARD);
        schedule.put(23, DayType.RANDOM_EVENT);
        schedule.put(24, DayType.PART_TIME_AVAILABLE);
        schedule.put(25, DayType.RANDOM_EVENT);
        schedule.put(26, DayType.STANDARD);
        schedule.put(27, DayType.RANDOM_EVENT);
        schedule.put(28, DayType.PART_TIME_AVAILABLE);

        // Last 2 Days: Endgame
        schedule.put(29, DayType.RENT_AND_UTILITIES);
        schedule.put(30, DayType.TRANSPORT_AND_ENDING);

        return Collections.unmodifiableMap(schedule);
    }
}
