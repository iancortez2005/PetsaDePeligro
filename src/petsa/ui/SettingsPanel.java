package petsa.ui;

import petsa.ui.PixelKit.BackgroundPanel;
import petsa.ui.PixelKit.FadePane;
import petsa.ui.PixelKit.PaperCard;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class SettingsPanel extends BackgroundPanel {

    public interface Actions {
        boolean deleteAllSaves();

        boolean clearLeaderboard();

        void onBack();
    }

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final int CARD_X = 189;
    private static final int CARD_Y = 123;
    private static final int CARD_W = 903;
    private static final int CARD_H = 474;
    private static final int LABEL_X = 36;
    private static final int CONTROL_X = 600;
    private static final int ROW_H = 66;
    private static final int FIRST_ROW = 24;

    private static final Color INK = new Color(0x3B, 0x2A, 0x1C);
    private static final Color DIM = new Color(0x7A, 0x6A, 0x58);

    private final GameSettings settings;
    private final JTextField nameField;

    public SettingsPanel(GameSettings settings, Actions actions) {
        super("resources/menu_background.jpeg", "resources/menu_background.png");
        this.settings = settings;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        JLabel title = new JLabel("SETTINGS", SwingConstants.CENTER);
        title.setFont(PixelKit.font(40f));
        title.setForeground(new Color(0xE8, 0xC9, 0x5A));
        title.setBounds(0, 40, WIDTH, 56);
        add(title);

        PaperCard card = new PaperCard(PaperCard.CREAM, false);
        card.setBounds(CARD_X, CARD_Y, CARD_W, CARD_H);
        add(card);

        addRowText(card, 0, "PLAYER NAME", "Filled in for you when you record a score.");
        nameField = new JTextField(settings.getPlayerName());
        nameField.setFont(PixelKit.font(12f));
        nameField.setForeground(INK);
        nameField.setBackground(new Color(0xFB, 0xF7, 0xEE));
        nameField.setCaretColor(INK);
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x2A, 0x1D, 0x12), 3), BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        nameField.setBounds(CONTROL_X, rowY(0), 270, 45);
        nameField.addActionListener(e -> saveName());
        nameField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                saveName();
            }
        });
        card.add(nameField);

        addRowText(card, 1, "TUTORIAL", "Start new games with the welcome tutorial.");
        WoodButton tutorial = toggleButton(settings.isShowTutorial());
        tutorial.setBounds(CONTROL_X + 120, rowY(1), 150, 45);
        tutorial.addActionListener(e -> {
            settings.setShowTutorial(!settings.isShowTutorial());
            setToggle(tutorial, settings.isShowTutorial());
            saveSettings();
        });
        card.add(tutorial);

        addRowText(card, 2, "SCREEN FADES", "Quick fades between actions and screens.");
        WoodButton fades = toggleButton(settings.isScreenFades());
        fades.setBounds(CONTROL_X + 120, rowY(2), 150, 45);
        fades.addActionListener(e -> {
            settings.setScreenFades(!settings.isScreenFades());
            FadePane.setFadesEnabled(settings.isScreenFades());
            setToggle(fades, settings.isScreenFades());
            saveSettings();
        });
        card.add(fades);

        addRowText(card, 3, "END OF DAY SUMMARY", "Show the day's money summary after sleeping.");
        WoodButton daySummary = toggleButton(settings.isShowDaySummary());
        daySummary.setBounds(CONTROL_X + 120, rowY(3), 150, 45);
        daySummary.addActionListener(e -> {
            settings.setShowDaySummary(!settings.isShowDaySummary());
            setToggle(daySummary, settings.isShowDaySummary());
            saveSettings();
        });
        card.add(daySummary);

        addRowText(card, 4, "SAVED GAMES", "Erase every saved game. Load Game will be empty.");
        WoodButton deleteSaves = new WoodButton("DELETE ALL", null, WoodButton.RED);
        deleteSaves.setBounds(CONTROL_X + 120, rowY(4), 150, 45);
        deleteSaves.addActionListener(e -> {
            if (GameDialog.confirm(this, "Saved Games", "Erase every saved game? This can't be undone.",
                    "ERASE ALL", "CANCEL", GameDialog.Tone.WARNING) && actions.deleteAllSaves()) {
                GameDialog.showMessage(this, "Saved Games", "All saved games were erased.");
            }
        });
        card.add(deleteSaves);

        addRowText(card, 5, "LEADERBOARD", "Erase every score on the leaderboard.");
        WoodButton clearScores = new WoodButton("CLEAR ALL", null, WoodButton.RED);
        clearScores.setBounds(CONTROL_X + 120, rowY(5), 150, 45);
        clearScores.addActionListener(e -> {
            if (GameDialog.confirm(this, "Leaderboard", "Erase every score on the leaderboard? This can't be undone.",
                    "CLEAR ALL", "CANCEL", GameDialog.Tone.WARNING) && actions.clearLeaderboard()) {
                GameDialog.showMessage(this, "Leaderboard", "The leaderboard was cleared.");
            }
        });
        card.add(clearScores);

        JLabel about = new JLabel("PETSA DE PELIGRO v1.0  -  CSS123P term project (Group 1)  -  Java Swing",
                SwingConstants.CENTER);
        about.setFont(PixelKit.font(9f));
        about.setForeground(DIM);
        about.setBounds(0, CARD_H - 48, CARD_W - 6, 22);
        card.add(about);

        WoodButton back = new WoodButton("< BACK TO MAIN MENU", null, WoodButton.BLUE);
        back.setBounds(440, 627, 400, 60);
        back.addActionListener(e -> {
            saveName();
            FadePane.run(this, actions::onBack);
        });
        add(back);
    }

    private static int rowY(int row) {
        return FIRST_ROW + row * ROW_H;
    }

    private void addRowText(PaperCard card, int row, String label, String description) {
        JLabel name = new JLabel(label);
        name.setFont(PixelKit.font(14f));
        name.setForeground(INK);
        name.setBounds(LABEL_X, rowY(row), 540, 24);
        card.add(name);
        JLabel desc = new JLabel(description);
        desc.setFont(PixelKit.font(9f));
        desc.setForeground(DIM);
        desc.setBounds(LABEL_X, rowY(row) + 26, 540, 20);
        card.add(desc);
    }

    private static WoodButton toggleButton(boolean on) {
        WoodButton button = new WoodButton("", null, WoodButton.GREEN);
        setToggle(button, on);
        return button;
    }

    private static void setToggle(WoodButton button, boolean on) {
        button.setTitleAndColor(on ? "ON" : "OFF", on ? WoodButton.GREEN : WoodButton.RED);
    }

    private void saveName() {
        settings.setPlayerName(nameField.getText());
        nameField.setText(settings.getPlayerName());
        saveSettings();
    }

    private void saveSettings() {
        try {
            settings.save();
        } catch (IOException ex) {
            GameDialog.showMessage(this, "Settings", "Couldn't save your settings:\n" + ex.getMessage(),
                    GameDialog.Tone.ERROR);
        }
    }

    public static class GameSettings {

        private static final String DEFAULT_NAME = "Player";
        private static final int MAX_NAME_LENGTH = 16;

        private final Path file;
        private String playerName = DEFAULT_NAME;
        private boolean showTutorial = true;
        private boolean screenFades = true;
        private boolean showDaySummary = true;

        public GameSettings(Path file) {
            this.file = file;
        }

        public void load() {
            if (!Files.exists(file)) {
                return;
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("GameSettings: couldn't read " + file + " (" + e.getMessage() + ") - using defaults.");
                return;
            }
            setPlayerName(props.getProperty("playerName", DEFAULT_NAME));
            showTutorial = Boolean.parseBoolean(props.getProperty("showTutorial", "true"));
            screenFades = Boolean.parseBoolean(props.getProperty("screenFades", "true"));
            showDaySummary = Boolean.parseBoolean(props.getProperty("showDaySummary", "true"));
        }

        public void save() throws IOException {
            Properties props = new Properties();
            props.setProperty("playerName", playerName);
            props.setProperty("showTutorial", String.valueOf(showTutorial));
            props.setProperty("screenFades", String.valueOf(screenFades));
            props.setProperty("showDaySummary", String.valueOf(showDaySummary));
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Petsa de Peligro settings");
            }
        }

        public String getPlayerName() { return playerName; }

        public void setPlayerName(String name) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                trimmed = DEFAULT_NAME;
            }
            playerName = trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
        }

        public boolean isShowTutorial() { return showTutorial; }
        public void setShowTutorial(boolean showTutorial) { this.showTutorial = showTutorial; }

        public boolean isScreenFades() { return screenFades; }
        public void setScreenFades(boolean screenFades) { this.screenFades = screenFades; }

        public boolean isShowDaySummary() { return showDaySummary; }
        public void setShowDaySummary(boolean showDaySummary) { this.showDaySummary = showDaySummary; }
    }
}
