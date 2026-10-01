package petsa.ui;

import petsa.model.DailyLedger.DaySummary;
import petsa.model.GameEngine;
import petsa.model.LeaderboardManager;
import petsa.model.SaveManager;
import petsa.model.SaveManager.GameSnapshot;
import petsa.ui.PixelKit.FadePane;
import petsa.ui.PixelKit.WoodButton;
import petsa.ui.SettingsPanel.GameSettings;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class GameApp {

    private static final float SCREEN_PEAK = 1f;
    private static final int SCREEN_OUT_MS = 160;
    private static final int SCREEN_IN_MS = 220;

    private final JFrame frame;
    private final LeaderboardManager leaderboard;
    private final SaveManager saves;
    private final GameSettings settings = new GameSettings(Paths.get("settings.properties"));

    private RoomPanel activeRoom;
    private GameEngine activeEngine;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameApp().start());
    }

    public GameApp() {
        this(Paths.get("saves.csv"), Paths.get("leaderboard.csv"), "Petsa de Peligro");
    }

    public static GameApp forTesting() {
        return new GameApp(Paths.get("saves_test.csv"), Paths.get("leaderboard_test.csv"),
                "Petsa de Peligro (test scenario)");
    }

    private GameApp(Path saveFile, Path leaderboardFile, String title) {
        this.saves = new SaveManager(saveFile);
        this.leaderboard = new LeaderboardManager(leaderboardFile);
        this.frame = new JFrame(title);
        FadePane.install(frame);
    }

    public void start() {
        settings.load();
        FadePane.setFadesEnabled(settings.isScreenFades());
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCloseRequested();
            }
        });
        frame.setResizable(false);
        showMenu();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void playFrom(GameEngine engine) {
        beginPlay(engine, false);
    }

    private void show(JComponent screen, Runnable afterShown) {
        Runnable swap = () -> {
            JLayeredPane layers = frame.getLayeredPane();
            for (Component overlay : layers.getComponentsInLayer(JLayeredPane.MODAL_LAYER)) {
                layers.remove(overlay);
            }
            frame.setContentPane(screen);
            frame.revalidate();
            frame.repaint();
        };
        if (!frame.isVisible()) {
            swap.run();
            frame.pack();
            if (afterShown != null) {
                afterShown.run();
            }
            return;
        }
        FadePane.run(frame, swap, SCREEN_PEAK, SCREEN_OUT_MS, SCREEN_IN_MS, afterShown);
    }

    private void show(JComponent screen) {
        show(screen, null);
    }

    private void onCloseRequested() {
        if (activeRoom == null || activeEngine == null || activeEngine.isGameOver()) {
            System.exit(0);
            return;
        }
        int choice = GameDialog.choose(frame, "Quit Game?",
                "You're in the middle of the month. Save your progress before quitting?\n"
                        + "Without saving, everything since your last save is lost.",
                new String[] {"SAVE & EXIT", "EXIT WITHOUT SAVING"},
                new Color[] {WoodButton.GREEN, WoodButton.RED}, "KEEP PLAYING");
        if (choice == 0) {
            if (activeRoom.saveNow()) {
                System.exit(0);
            }
        } else if (choice == 1) {
            System.exit(0);
        }
    }

    private void showMenu() {
        activeRoom = null;
        activeEngine = null;
        show(new MainMenuPanel(new MainMenuPanel.Listener() {
            @Override
            public void onNewGame() {
                startNewGame();
            }

            @Override
            public void onLoadGame() {
                showLoadGame();
            }

            @Override
            public void onExit() {
                System.exit(0);
            }

            @Override
            public void onSettings() {
                showSettings();
            }

            @Override
            public void onLeaderboard() {
                showLeaderboard(null);
            }
        }));
    }

    private void startNewGame() {
        boolean hasSaves;
        try {
            hasSaves = saves.hasCheckpoints();
        } catch (IOException | RuntimeException e) {
            hasSaves = true;
        }
        if (hasSaves) {
            if (!GameDialog.confirm(frame, "New Game", "Starting a new game will erase your saved games. Continue?",
                    "ERASE & START", "CANCEL", GameDialog.Tone.WARNING)) {
                return;
            }
            try {
                saves.deleteAll();
            } catch (IOException e) {
                GameDialog.showMessage(frame, "New Game", "Couldn't erase the old saves:\n" + e.getMessage(),
                        GameDialog.Tone.ERROR);
                return;
            }
        }
        beginPlay(new GameEngine(), true);
    }

    private void showLoadGame() {
        List<GameSnapshot> checkpoints;
        try {
            checkpoints = saves.loadAllCheckpoints();
        } catch (IOException | RuntimeException e) {
            GameDialog.showMessage(frame, "Load Game",
                    "Your save file (saves.csv) couldn't be read:\n" + e.getMessage()
                            + "\n\nStarting a New Game will replace it.",
                    GameDialog.Tone.ERROR);
            return;
        }
        show(new LoadGamePanel(checkpoints, new LoadGamePanel.Listener() {
            @Override
            public boolean onLoad(GameSnapshot snapshot) {
                if (!eraseLaterSaves(snapshot)) {
                    return false;
                }
                beginPlay(GameEngine.fromSnapshot(snapshot), false);
                return true;
            }

            @Override
            public void onBack() {
                showMenu();
            }

            @Override
            public void onNewGame() {
                startNewGame();
            }
        }));
    }

    private boolean eraseLaterSaves(GameSnapshot loading) {
        List<GameSnapshot> later;
        try {
            later = saves.checkpointsAfter(loading.getDay(), loading.getTimeSlot());
        } catch (IOException | RuntimeException e) {
            GameDialog.showMessage(frame, "Load Game", "Couldn't read the save file:\n" + e.getMessage(),
                    GameDialog.Tone.ERROR);
            return false;
        }
        if (later.isEmpty()) {
            return true;
        }
        StringBuilder list = new StringBuilder();
        int shown = 0;
        for (GameSnapshot s : later) {
            if (shown == 6) {
                list.append("\n...and ").append(later.size() - shown).append(" more");
                break;
            }
            list.append("\n- Day ").append(s.getDay()).append(", ").append(s.getTimeSlot().getLabel());
            shown++;
        }
        boolean go = GameDialog.confirm(frame, "Load Game",
                "Loading Day " + loading.getDay() + ", " + loading.getTimeSlot().getLabel()
                        + " rewinds the month, so these later saves will be erased:" + list,
                "LOAD & ERASE", "CANCEL", GameDialog.Tone.WARNING);
        if (!go) {
            return false;
        }
        try {
            saves.deleteCheckpointsAfter(loading.getDay(), loading.getTimeSlot());
            return true;
        } catch (IOException e) {
            GameDialog.showMessage(frame, "Load Game", "Couldn't update the save file:\n" + e.getMessage(),
                    GameDialog.Tone.ERROR);
            return false;
        }
    }

    private void beginPlay(GameEngine engine, boolean newGame) {
        if (engine.getState().getApartment() != null) {
            showRoom(engine, newGame);
            return;
        }
        if (newGame) {
            show(StoryboardPanel.opening(() -> showApartmentSelect(engine, true)));
        } else {
            showApartmentSelect(engine, false);
        }
    }

    private void showApartmentSelect(GameEngine engine, boolean newGame) {
        show(new ApartmentSelectPanel(apartment -> {
            engine.chooseApartment(apartment);
            showRoom(engine, newGame);
        }, this::showMenu));
    }

    private void showRoom(GameEngine engine, boolean newGame) {
        RoomPanel[] room = new RoomPanel[1];
        room[0] = new RoomPanel(engine, new RoomPanel.Listener() {
            @Override
            public void onDayEnded(DaySummary summary) {
                Runnable next = () -> {
                    if (summary.isFinalDay()) {
                        finishRun(summary.getClosingBalance());
                    } else if (engine.isMonthComplete()) {
                        showEnding(engine);
                    } else {
                        show(room[0], room[0]::returnFromEndOfDay);
                    }
                };
                if (settings.isShowDaySummary()) {
                    show(new EndOfDayPanel(summary, next));
                } else {
                    next.run();
                }
            }

            @Override
            public void onRestart() {
                startNewGame();
            }

            @Override
            public void onExitToTitle() {
                showMenu();
            }
        }, saves);
        activeRoom = room[0];
        activeEngine = engine;
        boolean tutorial = newGame && settings.isShowTutorial();
        show(room[0], tutorial ? room[0]::startWelcomeTutorial : room[0]::checkStatus);
    }

    private void showEnding(GameEngine engine) {
        activeRoom = null;
        activeEngine = null;
        int finalCash = engine.getState().getPlayer().getCash();
        show(StoryboardPanel.ending(finalCash, engine.getRentBill(), () -> finishRun(finalCash)));
    }

    private void finishRun(int finalCash) {
        activeRoom = null;
        activeEngine = null;
        String name = GameDialog.askText(frame, "Month Complete!",
                "You made it through the month with " + PixelKit.pesoCents(finalCash) + " left.\n"
                        + "Enter your name for the leaderboard:", settings.getPlayerName());
        if (name != null && !name.trim().isEmpty()) {
            settings.setPlayerName(name);
            try {
                settings.save();
            } catch (IOException e) {
                System.err.println("Couldn't save settings: " + e.getMessage());
            }
        }
        LeaderboardManager.Entry newEntry = null;
        try {
            newEntry = leaderboard.record(settings.getPlayerName(), finalCash);
        } catch (IOException e) {
            GameDialog.showMessage(frame, "Leaderboard", "Couldn't save your score: " + e.getMessage(),
                    GameDialog.Tone.WARNING);
        }
        showLeaderboard(newEntry);
    }

    private void showLeaderboard(LeaderboardManager.Entry highlight) {
        List<LeaderboardManager.Entry> entries;
        try {
            entries = leaderboard.loadAll();
        } catch (IOException | RuntimeException e) {
            GameDialog.showMessage(frame, "Leaderboard", "The leaderboard (leaderboard.csv) couldn't be read:\n"
                    + e.getMessage(), GameDialog.Tone.ERROR);
            showMenu();
            return;
        }
        show(new LeaderboardPanel(entries, highlight, this::showMenu));
    }

    private void showSettings() {
        show(new SettingsPanel(settings, new SettingsPanel.Actions() {
            @Override
            public boolean deleteAllSaves() {
                try {
                    saves.deleteAll();
                    return true;
                } catch (IOException e) {
                    GameDialog.showMessage(frame, "Saved Games", "Couldn't erase the saves:\n" + e.getMessage(),
                            GameDialog.Tone.ERROR);
                    return false;
                }
            }

            @Override
            public boolean clearLeaderboard() {
                try {
                    leaderboard.clearAll();
                    return true;
                } catch (IOException e) {
                    GameDialog.showMessage(frame, "Leaderboard", "Couldn't clear the leaderboard:\n" + e.getMessage(),
                            GameDialog.Tone.ERROR);
                    return false;
                }
            }

            @Override
            public void onBack() {
                showMenu();
            }
        }));
    }
}
