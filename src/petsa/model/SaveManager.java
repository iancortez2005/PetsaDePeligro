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

/**
 * Saves checkpoints (GameSnapshots, below) to saves.csv - one row per day
 * and time of day the player saved at, so Load Game can offer any saved
 * day AND time of day. Saving the same day and time again replaces that
 * row instead of adding a second one.
 *
 * Hand-rolled CSV, no external library: every field is a plain number,
 * true/false or enum name with no commas, so a simple join/split is safe.
 * The free-text fields (a Medical Bill reason, delayed-effect descriptions)
 * are cleaned of commas on write - see toRow()/encodeEffects(). Older save
 * files, with fewer columns, still load.
 */
public class SaveManager {

    private static final String HEADER = "day,timeSlot,apartmentType,todayWeather,"
            + "accumulatedWaterBill,cash,hunger,stress,academic,sickness,medicineStock,"
            + "foodStock,incapacitatedToday,ateToday,wentOutToday,medicalBillCount,"
            + "lastMedicalBillReason,pendingEffects,eventHandledToday,nightActionUsed,"
            + "ledgerStart,ledgerTotals,phoneBroken,studiedToday,showeredToday,rainyDays,eventHistory";

    private static final int FIELD_COUNT = 27;
    /** The oldest row layout still readable (18 columns); newer columns were always added at the end. */
    private static final int LEGACY_FIELD_COUNT = 18;

    // Sub-delimiters for the pendingEffects cell, chosen to avoid the CSV comma:
    // effects are joined by EFFECT_SEPARATOR; the 4 fields within one effect are
    // joined by EFFECT_FIELD_SEPARATOR.
    private static final String EFFECT_SEPARATOR = "\\^";
    private static final String EFFECT_FIELD_SEPARATOR = "\\|";

    private static final String NONE_MARKER = "-"; // for a null apartment, a null reason, or an empty effects list

    private final Path filePath;

    public SaveManager(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Writes/updates the checkpoint for snapshot's (day, timeSlot). If a
     * row for that same day+slot already exists, it is overwritten in
     * place; otherwise the row is appended. Rows are kept sorted by day
     * then slot order whenever the file is rewritten.
     */
    public void saveCheckpoint(GameSnapshot snapshot) throws IOException {
        List<GameSnapshot> all = new ArrayList<>(loadAllCheckpoints());
        all.removeIf(s -> s.getDay() == snapshot.getDay() && s.getTimeSlot() == snapshot.getTimeSlot());
        all.add(snapshot);
        all.sort(Comparator.comparingInt(GameSnapshot::getDay)
                .thenComparingInt(s -> s.getTimeSlot().ordinal()));
        writeAll(all);
    }

    /** Every checkpoint currently saved, in day/slot order. Returns an empty list if no save file exists yet. */
    public List<GameSnapshot> loadAllCheckpoints() throws IOException {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<GameSnapshot> result = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) { // skip the header row
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                result.add(parseRow(line));
            }
        }
        return result;
    }

    /** The specific checkpoint for the given day+slot, or null if none was ever saved for that exact point. */
    public GameSnapshot loadCheckpoint(int day, TimeSlot slot) throws IOException {
        for (GameSnapshot snapshot : loadAllCheckpoints()) {
            if (snapshot.getDay() == day && snapshot.getTimeSlot() == slot) {
                return snapshot;
            }
        }
        return null;
    }

    /** Every checkpoint from a later point in time than (day, slot): later days, and later times of the same day. */
    public List<GameSnapshot> checkpointsAfter(int day, TimeSlot slot) throws IOException {
        List<GameSnapshot> later = new ArrayList<>();
        for (GameSnapshot s : loadAllCheckpoints()) {
            if (isAfter(s, day, slot)) {
                later.add(s);
            }
        }
        return later;
    }

    /**
     * Erases every checkpoint from a later point in time than (day, slot).
     * Used when an earlier checkpoint is loaded, so saves from the
     * abandoned "future" never mix with the new one.
     */
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

    /** True if at least one checkpoint is saved. */
    public boolean hasCheckpoints() throws IOException {
        return !loadAllCheckpoints().isEmpty();
    }

    /** Erases every saved checkpoint (deletes the save file). Used when the player starts a new game. */
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
                : s.getLastMedicalBillReason().replace(",", ";"); // guard against a stray comma breaking the row
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

    /** A list cell: items joined by ';' (no commas, so the CSV stays intact), or NONE_MARKER if empty. */
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
                // a damaged entry - skip it
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
        // Older saves have fewer columns (newer ones were added at the end); anything from the
        // oldest layout up to the current one can be read, with sensible defaults for what's missing.
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
        // Older rows have no saved ledger: start the day's ledger from the saved cash instead.
        int ledgerStart = parts.length > 20 ? Integer.parseInt(parts[20]) : cash;
        Map<CashCategory, Integer> ledgerTotals = parts.length > 21
                ? decodeLedger(parts[21]) : new EnumMap<>(CashCategory.class);
        List<DailyLedger.EventEntry> ledgerEvents = parts.length > 21
                ? decodeLedgerEvents(parts[21]) : new ArrayList<>();

        return new GameSnapshot(day, slot, apartment, weather, waterBill, cash, hunger, stress,
                academic, sickness, medicineStock, foodStock, incapacitated, ate, wentOut,
                medicalBillCount, reason, effects, eventHandled, nightUsed, ledgerStart, ledgerTotals, ledgerEvents,
                parts.length > 22 && Boolean.parseBoolean(parts[22]),
                // Saves from before these were recorded: assume the day's study/shower happened,
                // so loading an old save never causes an unfair overnight penalty.
                parts.length <= 23 || Boolean.parseBoolean(parts[23]),
                parts.length <= 24 || Boolean.parseBoolean(parts[24]),
                // Saves from before the weather plan was saved get a fresh one on load (see GameEngine.fromSnapshot).
                parts.length > 25 ? decodeRainyDays(parts[25]) : new TreeSet<>(),
                parts.length > 26 ? decodeList(parts[26]) : new ArrayList<>());
    }

    /**
     * Ledger cell: non-zero totals as CATEGORY=amount, then each named
     * event as @Name=amount, all joined by ';' (no commas, so the CSV
     * stays intact). Older saves simply have no @ entries.
     */
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
                continue; // a named event line - read by decodeLedgerEvents()
            }
            String[] pair = token.split("=", 2);
            if (pair.length == 2) {
                try {
                    totals.put(CashCategory.valueOf(pair[0]), Integer.parseInt(pair[1]));
                } catch (IllegalArgumentException ignored) {
                    // an unknown category name from a future version - skip it rather than fail the load
                }
            }
        }
        return totals;
    }

    /** Reads the @Name=amount entries from a ledger cell. */
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
                    // a damaged entry - skip it; the End of Day summary will list the gap as "Other events"
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

    // ------------------------------------------------------------------
    // GameSnapshot
    // ------------------------------------------------------------------

    /**
     * Everything needed to resume a game at one day and time of day - one
     * row of saves.csv. Made by GameEngine.captureSnapshot() and turned back
     * into a game by GameEngine.fromSnapshot(); SaveManager only reads and
     * writes these fields.
     *
     * Snapshots are taken between actions (after the day's event, if any,
     * has been answered), so there is no field for a half-answered message.
     */
    public static class GameSnapshot {

        private final int day;
        private final TimeSlot timeSlot;
        private final ApartmentType apartment; // null only if saved before the apartment was chosen
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
        private final String lastMedicalBillReason; // null if no bill has been charged yet
        private final List<PendingEffect> pendingEffects;
        private final boolean eventHandledToday;   // today's random-event mail was already read and answered
        private final boolean nightActionUsed;     // the single Night action was already used
        private final int ledgerStartingBalance;   // today's End of Day ledger, so a loaded day's summary is complete
        private final Map<CashCategory, Integer> ledgerTotals;
        private final List<DailyLedger.EventEntry> ledgerEvents; // the named Random Events lines so far today
        private final boolean phoneBroken;
        private final boolean studiedToday;
        private final boolean showeredToday;
        private final Set<Integer> rainyDays;      // the month's weather plan; empty in saves from before it was saved
        private final List<String> eventHistory;   // random events delivered so far this month, oldest first

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
        /** A modifiable copy of the month's rainy days. */
        public Set<Integer> getRainyDays() { return new TreeSet<>(rainyDays); }
        public List<String> getEventHistory() { return eventHistory; }
    }
}