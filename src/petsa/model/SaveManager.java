package petsa.model;

import petsa.model.DailyLedger.CashCategory;
import petsa.model.GameState.ApartmentType;
import petsa.model.GameState.PendingEffect;
import petsa.model.GameState.Weather;
import petsa.model.Timeline.TimeSlot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SaveManager {

    private static final String HEADER = "day,timeSlot,apartmentType,todayWeather,"
            + "accumulatedWaterBill,cash,hunger,stress,academic,sickness,medicineStock,"
            + "foodStock,incapacitatedToday,ateToday,wentOutToday,medicalBillCount,"
            + "lastMedicalBillReason,pendingEffects,eventHandledToday,nightActionUsed,"
            + "ledgerStart,ledgerTotals,phoneBroken,studiedToday,showeredToday,rainyDays,eventHistory";

    private static final int FIELD_COUNT = 27;
    private static final int LEGACY_FIELD_COUNT = 18;

    private static final String EFFECT_SEPARATOR = "\\^";
    private static final String EFFECT_FIELD_SEPARATOR = "\\|";

    private static final String NONE_MARKER = "-";

    private final Path filePath;

    public SaveManager(Path filePath) {
        this.filePath = filePath;
    }

    public void saveCheckpoint(GameSnapshot snapshot) throws IOException {
        List<GameSnapshot> all = new ArrayList<>(loadAllCheckpoints());
        all.removeIf(s -> s.getDay() == snapshot.getDay() && s.getTimeSlot() == snapshot.getTimeSlot());
        all.add(snapshot);
        all.sort(Comparator.comparingInt(GameSnapshot::getDay)
                .thenComparingInt(s -> s.getTimeSlot().ordinal()));
        writeAll(all);
    }

    public List<GameSnapshot> loadAllCheckpoints() throws IOException {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<GameSnapshot> result = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                result.add(parseRow(line));
            }
        }
        return result;
    }

    public GameSnapshot loadCheckpoint(int day, TimeSlot slot) throws IOException {
        for (GameSnapshot snapshot : loadAllCheckpoints()) {
            if (snapshot.getDay() == day && snapshot.getTimeSlot() == slot) {
                return snapshot;
            }
        }
        return null;
    }

    public List<GameSnapshot> checkpointsAfter(int day, TimeSlot slot) throws IOException {
        List<GameSnapshot> later = new ArrayList<>();
        for (GameSnapshot s : loadAllCheckpoints()) {
            if (isAfter(s, day, slot)) {
                later.add(s);
            }
        }
        return later;
    }

    public void deleteCheckpointsAfter(int day, TimeSlot slot) throws IOException {
        List<GameSnapshot> kept = new ArrayList<>();
        for (GameSnapshot s : loadAllCheckpoints()) {
            if (!isAfter(s, day, slot)) {
                kept.add(s);
            }
        }
        writeAll(kept);
    }

    private static boolean isAfter(GameSnapshot s, int day, TimeSlot slot) {
        return s.getDay() > day || (s.getDay() == day && s.getTimeSlot().ordinal() > slot.ordinal());
    }

    public boolean hasCheckpoints() throws IOException {
        return !loadAllCheckpoints().isEmpty();
    }

    public void deleteAll() throws IOException {
        Files.deleteIfExists(filePath);
    }

    private void writeAll(List<GameSnapshot> snapshots) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (GameSnapshot s : snapshots) {
            lines.add(toRow(s));
        }
        Files.write(filePath, lines, StandardCharsets.UTF_8);
    }

    private String toRow(GameSnapshot s) {
        String apartment = (s.getApartment() == null) ? NONE_MARKER : s.getApartment().name();
        String reason = (s.getLastMedicalBillReason() == null)
                ? NONE_MARKER
                : s.getLastMedicalBillReason().replace(",", ";");
        String effects = encodeEffects(s.getPendingEffects());
        return String.join(",",
                String.valueOf(s.getDay()),
                s.getTimeSlot().name(),
                apartment,
                s.getTodayWeather().name(),
                String.valueOf(s.getAccumulatedWaterBill()),
                String.valueOf(s.getCash()),
                String.valueOf(s.getHunger()),
                String.valueOf(s.getStress()),
                String.valueOf(s.getAcademic()),
                String.valueOf(s.getSickness()),
                String.valueOf(s.getMedicineStock()),
                String.valueOf(s.getFoodStock()),
                String.valueOf(s.isIncapacitatedToday()),
                String.valueOf(s.isAteToday()),
                String.valueOf(s.isWentOutToday()),
                String.valueOf(s.getMedicalBillCount()),
                reason,
                effects,
                String.valueOf(s.isEventHandledToday()),
                String.valueOf(s.isNightActionUsed()),
                String.valueOf(s.getLedgerStartingBalance()),
                encodeLedger(s.getLedgerTotals(), s.getLedgerEvents()),
                String.valueOf(s.isPhoneBroken()),
                String.valueOf(s.isStudiedToday()),
                String.valueOf(s.isShoweredToday()),
                encodeList(s.getRainyDays()),
                encodeList(s.getEventHistory()));
    }

    private static String encodeList(Collection<?> items) {
        if (items.isEmpty()) {
            return NONE_MARKER;
        }
        List<String> safe = new ArrayList<>();
        for (Object item : items) {
            safe.add(String.valueOf(item).replaceAll("[;,]", " "));
        }
        return String.join(";", safe);
    }

    private static List<String> decodeList(String cell) {
        List<String> items = new ArrayList<>();
        if (cell == null || cell.isEmpty() || cell.equals(NONE_MARKER)) {
            return items;
        }
        for (String item : cell.split(";")) {
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private static Set<Integer> decodeRainyDays(String cell) {
        Set<Integer> days = new TreeSet<>();
        for (String item : decodeList(cell)) {
            try {
                days.add(Integer.parseInt(item.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return days;
    }

    private String encodeEffects(List<PendingEffect> effects) {
        if (effects.isEmpty()) {
            return NONE_MARKER;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) {
                sb.append('^');
            }
            PendingEffect e = effects.get(i);
            String safeDescription = e.getDescription().replace("|", "/").replace("^", "-").replace(",", ";");
            sb.append(e.getTriggerDay()).append('|')
                    .append(e.getTriggerSlot().name()).append('|')
                    .append(safeDescription).append('|')
                    .append(e.getCashDelta());
        }
        return sb.toString();
    }

    private GameSnapshot parseRow(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length < LEGACY_FIELD_COUNT || parts.length > FIELD_COUNT) {
            throw new IllegalStateException("Malformed save row (expected " + FIELD_COUNT
                    + " fields, got " + parts.length + "): " + line);
        }
        int day = Integer.parseInt(parts[0]);
        TimeSlot slot = TimeSlot.valueOf(parts[1]);
        ApartmentType apartment = parts[2].equals(NONE_MARKER) ? null : ApartmentType.valueOf(parts[2]);
        Weather weather = Weather.valueOf(parts[3]);
        int waterBill = Integer.parseInt(parts[4]);
        int cash = Integer.parseInt(parts[5]);
        int hunger = Integer.parseInt(parts[6]);
        int stress = Integer.parseInt(parts[7]);
        int academic = Integer.parseInt(parts[8]);
        int sickness = Integer.parseInt(parts[9]);
        int medicineStock = Integer.parseInt(parts[10]);
        int foodStock = Integer.parseInt(parts[11]);
        boolean incapacitated = Boolean.parseBoolean(parts[12]);
        boolean ate = Boolean.parseBoolean(parts[13]);
        boolean wentOut = Boolean.parseBoolean(parts[14]);
        int medicalBillCount = Integer.parseInt(parts[15]);
        String reason = parts[16].equals(NONE_MARKER) ? null : parts[16];
        List<PendingEffect> effects = decodeEffects(parts[17]);
        boolean eventHandled = parts.length > 18 && Boolean.parseBoolean(parts[18]);
        boolean nightUsed = parts.length > 19 && Boolean.parseBoolean(parts[19]);
        int ledgerStart = parts.length > 20 ? Integer.parseInt(parts[20]) : cash;
        Map<CashCategory, Integer> ledgerTotals = parts.length > 21
                ? decodeLedger(parts[21]) : new EnumMap<>(CashCategory.class);
        List<DailyLedger.EventEntry> ledgerEvents = parts.length > 21
                ? decodeLedgerEvents(parts[21]) : new ArrayList<>();

        return new GameSnapshot(day, slot, apartment, weather, waterBill, cash, hunger, stress,
                academic, sickness, medicineStock, foodStock, incapacitated, ate, wentOut,
                medicalBillCount, reason, effects, eventHandled, nightUsed, ledgerStart, ledgerTotals, ledgerEvents,
                parts.length > 22 && Boolean.parseBoolean(parts[22]),
                parts.length <= 23 || Boolean.parseBoolean(parts[23]),
                parts.length <= 24 || Boolean.parseBoolean(parts[24]),
                parts.length > 25 ? decodeRainyDays(parts[25]) : new TreeSet<>(),
                parts.length > 26 ? decodeList(parts[26]) : new ArrayList<>());
    }

    private String encodeLedger(Map<CashCategory, Integer> totals, List<DailyLedger.EventEntry> events) {
        StringBuilder sb = new StringBuilder();
        for (DailyLedger.EventEntry event : events) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            String safeLabel = event.getLabel().replaceAll("[;=,@]", " ");
            sb.append('@').append(safeLabel).append('=').append(event.getAmount());
        }
        for (Map.Entry<CashCategory, Integer> entry : totals.entrySet()) {
            if (entry.getValue() != 0) {
                if (sb.length() > 0) {
                    sb.append(';');
                }
                sb.append(entry.getKey().name()).append('=').append(entry.getValue());
            }
        }
        return sb.length() == 0 ? NONE_MARKER : sb.toString();
    }

    private Map<CashCategory, Integer> decodeLedger(String cell) {
        Map<CashCategory, Integer> totals = new EnumMap<>(CashCategory.class);
        if (cell == null || cell.isEmpty() || cell.equals(NONE_MARKER)) {
            return totals;
        }
        for (String token : cell.split(";")) {
            if (token.startsWith("@")) {
                continue;
            }
            String[] pair = token.split("=", 2);
            if (pair.length == 2) {
                try {
                    totals.put(CashCategory.valueOf(pair[0]), Integer.parseInt(pair[1]));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return totals;
    }

    private List<DailyLedger.EventEntry> decodeLedgerEvents(String cell) {
        List<DailyLedger.EventEntry> events = new ArrayList<>();
        if (cell == null || cell.isEmpty() || cell.equals(NONE_MARKER)) {
            return events;
        }
        for (String token : cell.split(";")) {
            if (!token.startsWith("@")) {
                continue;
            }
            String[] pair = token.substring(1).split("=", 2);
            if (pair.length == 2) {
                try {
                    events.add(new DailyLedger.EventEntry(pair[0], Integer.parseInt(pair[1])));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return events;
    }

    private List<PendingEffect> decodeEffects(String cell) {
        List<PendingEffect> effects = new ArrayList<>();
        if (cell == null || cell.isEmpty() || cell.equals(NONE_MARKER)) {
            return effects;
        }
        for (String token : cell.split(EFFECT_SEPARATOR)) {
            String[] fields = token.split(EFFECT_FIELD_SEPARATOR, -1);
            int triggerDay = Integer.parseInt(fields[0]);
            TimeSlot triggerSlot = TimeSlot.valueOf(fields[1]);
            String description = fields[2];
            int cashDelta = Integer.parseInt(fields[3]);
            effects.add(new PendingEffect(triggerDay, triggerSlot, description, cashDelta));
        }
        return effects;
    }

    public static class GameSnapshot {

        private final int day;
        private final TimeSlot timeSlot;
        private final ApartmentType apartment;
        private final Weather todayWeather;
        private final int accumulatedWaterBill;
        private final int cash;
        private final int hunger;
        private final int stress;
        private final int academic;
        private final int sickness;
        private final int medicineStock;
        private final int foodStock;
        private final boolean incapacitatedToday;
        private final boolean ateToday;
        private final boolean wentOutToday;
        private final int medicalBillCount;
        private final String lastMedicalBillReason;
        private final List<PendingEffect> pendingEffects;
        private final boolean eventHandledToday;
        private final boolean nightActionUsed;
        private final int ledgerStartingBalance;
        private final Map<CashCategory, Integer> ledgerTotals;
        private final List<DailyLedger.EventEntry> ledgerEvents;
        private final boolean phoneBroken;
        private final boolean studiedToday;
        private final boolean showeredToday;
        private final Set<Integer> rainyDays;
        private final List<String> eventHistory;

        public GameSnapshot(int day, TimeSlot timeSlot, ApartmentType apartment, Weather todayWeather,
                int accumulatedWaterBill, int cash, int hunger, int stress, int academic, int sickness,
                int medicineStock, int foodStock, boolean incapacitatedToday, boolean ateToday,
                boolean wentOutToday, int medicalBillCount, String lastMedicalBillReason,
                List<PendingEffect> pendingEffects, boolean eventHandledToday, boolean nightActionUsed,
                int ledgerStartingBalance, Map<CashCategory, Integer> ledgerTotals,
                List<DailyLedger.EventEntry> ledgerEvents, boolean phoneBroken,
                boolean studiedToday, boolean showeredToday, Set<Integer> rainyDays, List<String> eventHistory) {
            this.day = day;
            this.timeSlot = timeSlot;
            this.apartment = apartment;
            this.todayWeather = todayWeather;
            this.accumulatedWaterBill = accumulatedWaterBill;
            this.cash = cash;
            this.hunger = hunger;
            this.stress = stress;
            this.academic = academic;
            this.sickness = sickness;
            this.medicineStock = medicineStock;
            this.foodStock = foodStock;
            this.incapacitatedToday = incapacitatedToday;
            this.ateToday = ateToday;
            this.wentOutToday = wentOutToday;
            this.medicalBillCount = medicalBillCount;
            this.lastMedicalBillReason = lastMedicalBillReason;
            this.pendingEffects = Collections.unmodifiableList(pendingEffects);
            this.eventHandledToday = eventHandledToday;
            this.nightActionUsed = nightActionUsed;
            this.ledgerStartingBalance = ledgerStartingBalance;
            Map<CashCategory, Integer> totalsCopy = new EnumMap<>(CashCategory.class);
            totalsCopy.putAll(ledgerTotals);
            this.ledgerTotals = Collections.unmodifiableMap(totalsCopy);
            this.ledgerEvents = Collections.unmodifiableList(new ArrayList<>(ledgerEvents));
            this.phoneBroken = phoneBroken;
            this.studiedToday = studiedToday;
            this.showeredToday = showeredToday;
            this.rainyDays = Collections.unmodifiableSet(new TreeSet<>(rainyDays));
            this.eventHistory = Collections.unmodifiableList(new ArrayList<>(eventHistory));
        }

        public int getDay() { return day; }
        public TimeSlot getTimeSlot() { return timeSlot; }
        public ApartmentType getApartment() { return apartment; }
        public Weather getTodayWeather() { return todayWeather; }
        public int getAccumulatedWaterBill() { return accumulatedWaterBill; }
        public int getCash() { return cash; }
        public int getHunger() { return hunger; }
        public int getStress() { return stress; }
        public int getAcademic() { return academic; }
        public int getSickness() { return sickness; }
        public int getMedicineStock() { return medicineStock; }
        public int getFoodStock() { return foodStock; }
        public boolean isIncapacitatedToday() { return incapacitatedToday; }
        public boolean isAteToday() { return ateToday; }
        public boolean isWentOutToday() { return wentOutToday; }
        public int getMedicalBillCount() { return medicalBillCount; }
        public String getLastMedicalBillReason() { return lastMedicalBillReason; }
        public List<PendingEffect> getPendingEffects() { return pendingEffects; }
        public boolean isEventHandledToday() { return eventHandledToday; }
        public boolean isNightActionUsed() { return nightActionUsed; }
        public int getLedgerStartingBalance() { return ledgerStartingBalance; }
        public Map<CashCategory, Integer> getLedgerTotals() { return ledgerTotals; }
        public List<DailyLedger.EventEntry> getLedgerEvents() { return ledgerEvents; }
        public boolean isPhoneBroken() { return phoneBroken; }
        public boolean isStudiedToday() { return studiedToday; }
        public boolean isShoweredToday() { return showeredToday; }
        public Set<Integer> getRainyDays() { return new TreeSet<>(rainyDays); }
        public List<String> getEventHistory() { return eventHistory; }
    }
}
