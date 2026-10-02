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
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The game's entry point - run this class to play. It owns the window and
 * decides which screen comes next:
 *
 *   Main Menu -> New Game -> opening storyboard -> apartment choice ->
 *   Room (the welcome tutorial on a new game) -> Bed -> End of Day summary
 *   (unless turned off in Settings) -> the next morning -> ... -> Day 29
 *   (Rent Day) -> Bed -> ending storyboard -> name for the leaderboard ->
 *   Leaderboard -> Main Menu.
 *
 * Losing (going broke, or not affording the fare home) shows the Game Over
 * screen over the room, with RESTART or EXIT GAME.
 *
 * Saving: SAVE & EXIT on the pause screen, or closing the window mid-game,
 * writes a checkpoint for the current day and time of day to saves.csv.
 * The game's files live in a folder of the player's own (see dataFile()),
 * so they can be written even when the game is installed somewhere
 * read-only like Program Files.
 * Loading: Load Game lists every saved day, then that day's saved times.
 * All checkpoints belong to one month, so starting a New Game while saves
 * exist asks first, then erases them.
 *
 * Settings live in settings.properties (see SettingsPanel.GameSettings).
 */
public class GameApp {

    private static final float SCREEN_PEAK = 1f;
    private static final int SCREEN_OUT_MS = 160;
    private static final int SCREEN_IN_MS = 220;

    private final JFrame frame;
    private final LeaderboardManager leaderboard;
    private final SaveManager saves;
    private final GameSettings settings = new GameSettings(dataFile("settings.properties"));

    // The game in progress, while the player is in the room or reading an End of Day summary; null otherwise.
    private RoomPanel activeRoom;
    private GameEngine activeEngine;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameApp().start());
    }

    public GameApp() {
        this(dataFile("saves.csv"), dataFile("leaderboard.csv"), "Petsa de Peligro");
    }

    /**
     * For the test launcher (petsa.demo.ScenarioLauncher): the same game, but
     * with its own save and leaderboard files, so the real ones are never
     * touched while testing.
     */
    public static GameApp forTesting() {
        return new GameApp(dataFile("saves_test.csv"), dataFile("leaderboard_test.csv"),
                "Petsa de Peligro (test scenario)");
    }

    /**
     * Where the game keeps one of its files (saves, leaderboard, settings):
     * "%APPDATA%\Petsa de Peligro" on Windows, "~/.petsa-de-peligro"
     * elsewhere. Older versions kept these files in the folder the game was
     * started from; if one is found there and not yet in the new folder, it
     * is copied over once, so saves and scores carry across.
     */
    public static Path dataFile(String name) {
        String appData = System.getenv("APPDATA");
        Path folder = (appData != null)
                ? Paths.get(appData, "Petsa de Peligro")
                : Paths.get(System.getProperty("user.home"), ".petsa-de-peligro");
        Path file = folder.resolve(name);
        Path oldFile = Paths.get(name).toAbsolutePath();
        try {
            Files.createDirectories(folder);
            if (!Files.exists(file) && Files.exists(oldFile)) {
                Files.copy(oldFile, file);
            }
        } catch (IOException e) {
            System.err.println("GameApp: couldn't prepare " + file + " (" + e.getMessage() + ")");
        }
        return file;
    }

    private GameApp(Path saveFile, Path leaderboardFile, String title) {
        this.saves = new SaveManager(saveFile);
        this.leaderboard = new LeaderboardManager(leaderboardFile);
        this.frame = new JFrame(title);
        setWindowIcon(frame);
        FadePane.install(frame);
    }

    /** The wallet icon on the title bar, taskbar and Alt+Tab, at the sizes Windows asks for. */
    private static void setWindowIcon(JFrame frame) {
        Image icon = PixelKit.loadImage("resources/app_icon.png");
        if (icon == null) {
            return;
        }
        List<Image> sizes = new ArrayList<>();
        for (int size : new int[] {16, 20, 24, 32, 40, 48, 64}) {
            sizes.add(icon.getScaledInstance(size, size, Image.SCALE_SMOOTH));
        }
        sizes.add(icon);
        frame.setIconImages(sizes);
    }

    /** Opens the window on the Main Menu. */
    public void start() {
        settings.load();
        FadePane.setFadesEnabled(settings.isScreenFades());
        // Closing the window mid-game asks first (see onCloseRequested), so progress isn't lost by accident.
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

    /** For the test launcher: jumps straight into the room with a game that's already set up (no tutorial). */
    public void playFrom(GameEngine engine) {
        beginPlay(engine, false);
    }

    /**
     * Shows screen. The very first screen (before the window is visible)
     * appears instantly; later ones fade through black. afterShown (may be
     * null) runs once the new screen has fully faded in.
     */
    private void show(JComponent screen, Runnable afterShown) {
        Runnable swap = () -> {
            // Take any overlays (pause, phone, board, Game Over...) off the window first: they live on
            // the layered pane above the old screen and would otherwise stay on top of the new one.
            // This must happen here, before setContentPane - removing them from inside the old screen's
            // removeNotify() would change the layered pane in the middle of Swing's own remove() and crash.
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

    /**
     * The window's close button. Outside of a game (menus, leaderboard,
     * Game Over) it simply quits. During a game, the player picks:
     * SAVE & EXIT (writes a checkpoint for the current day, time and
     * weather, then quits), EXIT WITHOUT SAVING, or KEEP PLAYING.
     */
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
            // Saving failed: the player was told why and stays in the game.
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

    /**
     * Starts a brand-new game. If saved games exist, the player confirms
     * first, since the new playthrough replaces them.
     */
    private void startNewGame() {
        boolean hasSaves;
        try {
            hasSaves = saves.hasCheckpoints();
        } catch (IOException | RuntimeException e) {
            hasSaves = true; // an unreadable save file still counts - ask before overwriting it
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

    /** Shows the Load Game screen with every saved checkpoint. */
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
                    return false; // the player changed their mind - stay on the Load Game screen
                }
                // A checkpoint already remembers its apartment, so this goes straight to the room.
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

    /**
     * Loading a checkpoint rewinds time, so every save from a later point
     * (later days, and later times on the same day) is erased - otherwise
     * saves from two different versions of the month would mix. Asks first,
     * listing what will go. Returns false if the player cancels or the save
     * file can't be updated (nothing is erased and nothing is loaded then).
     */
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

    /**
     * Starts or resumes play with the given game. If no apartment has been
     * chosen yet, the player picks one first; otherwise they go straight to
     * the room. A brand-new game (New Game, or restarting from Game Over)
     * opens with the moving-to-the-city storyboard before the apartment
     * choice.
     */
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

    /** newGame: show the welcome tutorial once the room has faded in (loaded games skip it). */
    private void showRoom(GameEngine engine, boolean newGame) {
        RoomPanel[] room = new RoomPanel[1]; // array so the listener can refer to the room it belongs to
        room[0] = new RoomPanel(engine, new RoomPanel.Listener() {
            @Override
            public void onDayEnded(DaySummary summary) {
                Runnable next = () -> {
                    if (summary.isFinalDay()) {
                        finishRun(summary.getClosingBalance());
                    } else if (engine.isMonthComplete()) {
                        // Day 29 is over and the fare home is paid: Day 30 is the ending, not another day.
                        showEnding(engine);
                    } else {
                        // Includes Day 30 when the player couldn't afford the fare: the room shows Game Over.
                        // With the summary turned off this re-shows the same room, which still fades through
                        // black - so going to bed still reads as the night passing.
                        show(room[0], room[0]::returnFromEndOfDay);
                    }
                };
                if (settings.isShowDaySummary()) {
                    show(new EndOfDayPanel(summary, next));
                } else {
                    next.run(); // Settings > END OF DAY SUMMARY is off: straight to the next morning
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

    /** The won month's storyboard: the bus home, then FINISH records the score. */
    private void showEnding(GameEngine engine) {
        activeRoom = null; // the month is won - nothing left to save
        activeEngine = null;
        int finalCash = engine.getState().getPlayer().getCash();
        show(StoryboardPanel.ending(finalCash, engine.getRentBill(), () -> finishRun(finalCash)));
    }

    /**
     * Records the finished month on the leaderboard (asking for a name,
     * pre-filled from Settings) and shows the leaderboard with the new
     * result highlighted. The name used becomes the default next time.
     */
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

    /** The leaderboard screen. highlight: a result to highlight (the run just finished), or null. */
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
