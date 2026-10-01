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

public class LeaderboardManager {

    private static final String HEADER = "playerName,finalSavings,recordedAt";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path filePath;

    public LeaderboardManager(Path filePath) {
        this.filePath = filePath;
    }

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

        public boolean sameAs(Entry other) {
            return other != null && playerName.equals(other.playerName) && finalSavings == other.finalSavings
                    && recordedAt.equals(other.recordedAt);
        }
    }

    public List<Entry> recordScore(String playerName, int finalSavings) throws IOException {
        List<Entry> all = new ArrayList<>(loadAll());
        String safeName = playerName.replace(",", " ");
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        all.add(new Entry(safeName, finalSavings, timestamp));
        all.sort(Comparator.comparingInt(Entry::getFinalSavings).reversed());
        writeAll(all);
        return all;
    }

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

    public void clearAll() throws IOException {
        Files.deleteIfExists(filePath);
    }

    public List<Entry> loadAll() throws IOException {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<Entry> result = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
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
