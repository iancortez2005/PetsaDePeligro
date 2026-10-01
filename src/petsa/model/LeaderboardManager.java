package petsa.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The leaderboard, kept in leaderboard.csv: every won month with the
 * player's name, the money left after the fare home, and when it was
 * recorded - ranked highest savings first.
 *
 * The player's name comes from Settings (it isn't part of Player, which
 * only holds gameplay state).
 */
public class LeaderboardManager {

    private static final String HEADER = "playerName,finalSavings,recordedAt";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path filePath;

    public LeaderboardManager(Path filePath) {
        this.filePath = filePath;
    }

    /** One leaderboard row: a player's name, their final savings, and when the result was recorded. */
    public static class Entry {
        private final String playerName;
        private final int finalSavings;
        private final String recordedAt;

        public Entry(String playerName, int finalSavings, String recordedAt) {
            this.playerName = playerName;
            this.finalSavings = finalSavings;
            this.recordedAt = recordedAt;
        }

        public String getPlayerName() { return playerName; }
        public int getFinalSavings() { return finalSavings; }
        public String getRecordedAt() { return recordedAt; }

        /** True if other is the same result (same name, savings and time) - e.g. the same row re-read from disk. */
        public boolean sameAs(Entry other) {
            return other != null && playerName.equals(other.playerName) && finalSavings == other.finalSavings
                    && recordedAt.equals(other.recordedAt);
        }
    }

    /**
     * Appends a new result (timestamped automatically) and returns the
     * full leaderboard, sorted highest-savings first.
     */
    public List<Entry> recordScore(String playerName, int finalSavings) throws IOException {
        List<Entry> all = new ArrayList<>(loadAll());
        String safeName = playerName.replace(",", " "); // guard against a comma breaking the row
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        all.add(new Entry(safeName, finalSavings, timestamp));
        all.sort(Comparator.comparingInt(Entry::getFinalSavings).reversed());
        writeAll(all);
        return all;
    }

    /** Records a result like recordScore(), but returns the new entry itself (e.g. to highlight it on the leaderboard). */
    public Entry record(String playerName, int finalSavings) throws IOException {
        List<Entry> all = recordScore(playerName, finalSavings);
        String safeName = playerName.replace(",", " ");
        Entry newest = null;
        for (Entry e : all) {
            if (e.getPlayerName().equals(safeName) && e.getFinalSavings() == finalSavings
                    && (newest == null || e.getRecordedAt().compareTo(newest.getRecordedAt()) > 0)) {
                newest = e;
            }
        }
        return newest;
    }

    /** Erases every recorded result (deletes the leaderboard file). */
    public void clearAll() throws IOException {
        Files.deleteIfExists(filePath);
    }

    /** Every recorded result, in the order stored on disk (already sorted descending after the last recordScore()). */
    public List<Entry> loadAll() throws IOException {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<Entry> result = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) { // skip the header row
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",", -1);
            result.add(new Entry(parts[0], Integer.parseInt(parts[1]), parts[2]));
        }
        return result;
    }

    private void writeAll(List<Entry> entries) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Entry e : entries) {
            lines.add(e.getPlayerName() + "," + e.getFinalSavings() + "," + e.getRecordedAt());
        }
        Files.write(filePath, lines, StandardCharsets.UTF_8);
    }
}
