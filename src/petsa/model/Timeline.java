package petsa.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Timeline {

    public enum DayType {
        STANDARD_TUTORIAL,
        STANDARD,
        RANDOM_EVENT,
        PART_TIME_AVAILABLE,
        RENT_AND_UTILITIES,
        TRANSPORT_AND_ENDING
    }

    public enum TimeSlot {
        MORNING("Morning"),
        AFTERNOON("Mid-day"),
        NIGHT("Night");

        private final String label;

        TimeSlot(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public TimeSlot next() {
            switch (this) {
                case MORNING:   return AFTERNOON;
                case AFTERNOON: return NIGHT;
                default:        return null;
            }
        }
    }

    private static final Map<Integer, DayType> SCHEDULE = buildSchedule();

    public static final List<Integer> RENT_REMINDER_DAYS = Collections.unmodifiableList(Arrays.asList(7, 14, 21, 28));

    private Timeline() {
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

        schedule.put(1, DayType.STANDARD_TUTORIAL);
        schedule.put(2, DayType.STANDARD);
        schedule.put(3, DayType.STANDARD);
        schedule.put(4, DayType.RANDOM_EVENT);
        schedule.put(5, DayType.STANDARD);
        schedule.put(6, DayType.STANDARD);
        schedule.put(7, DayType.PART_TIME_AVAILABLE);

        schedule.put(8, DayType.STANDARD);
        schedule.put(9, DayType.RANDOM_EVENT);
        schedule.put(10, DayType.STANDARD);
        schedule.put(11, DayType.STANDARD);
        schedule.put(12, DayType.RANDOM_EVENT);
        schedule.put(13, DayType.STANDARD);
        schedule.put(14, DayType.PART_TIME_AVAILABLE);

        schedule.put(15, DayType.STANDARD);
        schedule.put(16, DayType.RANDOM_EVENT);
        schedule.put(17, DayType.STANDARD);
        schedule.put(18, DayType.RANDOM_EVENT);
        schedule.put(19, DayType.STANDARD);
        schedule.put(20, DayType.RANDOM_EVENT);
        schedule.put(21, DayType.PART_TIME_AVAILABLE);

        schedule.put(22, DayType.STANDARD);
        schedule.put(23, DayType.RANDOM_EVENT);
        schedule.put(24, DayType.PART_TIME_AVAILABLE);
        schedule.put(25, DayType.RANDOM_EVENT);
        schedule.put(26, DayType.STANDARD);
        schedule.put(27, DayType.RANDOM_EVENT);
        schedule.put(28, DayType.PART_TIME_AVAILABLE);

        schedule.put(29, DayType.RENT_AND_UTILITIES);
        schedule.put(30, DayType.TRANSPORT_AND_ENDING);

        return Collections.unmodifiableMap(schedule);
    }
}
