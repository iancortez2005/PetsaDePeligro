package petsa.demo;

import petsa.model.DailyLedger.CashCategory;
import petsa.model.GameEngine;
import petsa.model.GameState;
import petsa.model.GameState.ApartmentType;
import petsa.model.GameState.Weather;
import petsa.model.PartTimeJob.ShiftType;
import petsa.model.Player;
import petsa.model.RandomEvent;
import petsa.model.RandomEvent.AcademicCommissionEvent;
import petsa.model.RandomEvent.PickpocketedEvent;
import petsa.model.RandomEvent.RainyDayLaundryEvent;
import petsa.model.SaveManager;
import petsa.model.SaveManager.GameSnapshot;
import petsa.model.Task;
import petsa.model.Task.EatTask;
import petsa.model.Task.HygieneTask;
import petsa.model.Task.StudyTask;
import petsa.model.Task.WalkTask;
import petsa.model.Timeline.DayType;
import petsa.model.Timeline.TimeSlot;
import petsa.ui.GameApp;
import petsa.ui.GameDialog;
import petsa.ui.PixelKit;
import petsa.ui.PixelKit.BackgroundPanel;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;

public class ScenarioLauncher {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final String P = "₱";

    private static final class Scenario {
        final String title;
        final String subtitle;
        final Color color;
        final Runnable action;

        Scenario(String title, String subtitle, Color color, Runnable action) {
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
            this.action = action;
        }
    }

    private final JFrame frame = new JFrame("Petsa de Peligro - Test Scenarios");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ScenarioLauncher().show());
    }

    private void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setContentPane(buildPanel());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private BackgroundPanel buildPanel() {
        BackgroundPanel panel = new BackgroundPanel("resources/menu_background.jpeg", "resources/menu_background.png");
        panel.setPreferredSize(new Dimension(WIDTH, HEIGHT));

        JLabel title = new JLabel("TEST SCENARIOS", SwingConstants.CENTER);
        title.setFont(PixelKit.font(34f));
        title.setForeground(new Color(0xE8, 0xC9, 0x5A));
        title.setBounds(0, 26, WIDTH, 48);
        panel.add(title);

        JLabel note = new JLabel("Pick a situation and the real game opens right there. "
                + "Test games use saves_test.csv and leaderboard_test.csv.", SwingConstants.CENTER);
        note.setFont(PixelKit.font(9f));
        note.setForeground(new Color(0xE8, 0xDD, 0xC0));
        note.setBounds(0, 82, WIDTH, 20);
        panel.add(note);

        List<Scenario> scenarios = scenarios();
        for (int i = 0; i < scenarios.size(); i++) {
            Scenario scenario = scenarios.get(i);
            WoodButton button = new WoodButton(scenario.title, scenario.subtitle, scenario.color);
            button.setBounds(i % 2 == 0 ? 70 : 650, 118 + (i / 2) * 68, 560, 58);
            button.addActionListener(e -> scenario.action.run());
            panel.add(button);
        }

        WoodButton close = new WoodButton("CLOSE", null, WoodButton.TAN);
        close.setBounds((WIDTH - 200) / 2, 640, 200, 50);
        close.addActionListener(e -> System.exit(0));
        panel.add(close);
        return panel;
    }

    private List<Scenario> scenarios() {
        List<Scenario> list = new ArrayList<>();
        list.add(new Scenario("THE ENDING", "Day 29 night: do a task, sleep, ride the bus home", WoodButton.GREEN,
                () -> play(new Setup().at(29, TimeSlot.NIGHT).cash(2400).didEverything()::build)));
        list.add(new Scenario("RENT DAY", "Day 28 night: sleep, then the landlord comes", WoodButton.TAN,
                () -> play(new Setup().at(28, TimeSlot.NIGHT).cash(3200).water(360).didEverything()::build)));
        list.add(new Scenario("STRANDED", "Day 29 night with " + P + "350: can't pay the fare home", WoodButton.RED,
                () -> play(new Setup().at(29, TimeSlot.NIGHT).cash(350).didEverything()::build)));
        list.add(new Scenario("BANKRUPT AT RENT", "Day 28 night with " + P + "1,000: the bills break you",
                WoodButton.RED, () -> play(new Setup().at(28, TimeSlot.NIGHT).cash(1000).water(300)
                        .didEverything()::build)));
        list.add(new Scenario("STRESS AT 100%", "Job night at max Stress: work and get pickpocketed?",
                WoodButton.ORANGE, () -> play(new Setup().at(7, TimeSlot.NIGHT).cash(3600).stress(100)
                        .didEverything()::build)));
        list.add(new Scenario("HUNGER COLLAPSE", "Hunger 25%: walk instead of eating, then sleep",
                WoodButton.ORANGE, () -> play(new Setup().at(10, TimeSlot.NIGHT).cash(3000).hunger(25)
                        .studied().showered()::build)));
        list.add(new Scenario("RAINY DAY", "Rain all day: laundromat, colds, the rainy window", WoodButton.BLUE,
                () -> play(new Setup().at(13, TimeSlot.MORNING).cash(3000).medicine(2).rainyToday()::build)));
        list.add(new Scenario("EVENT DAY", "Day 4: a random event is waiting on the phone", WoodButton.BLUE,
                () -> play(new Setup().at(4, TimeSlot.MORNING).cash(4500).eventWaiting()::build)));
        list.add(new Scenario("JOB LOCKED", "Academics 60%: the part-time job won't take you",
                WoodButton.PURPLE, () -> play(new Setup().at(14, TimeSlot.NIGHT).cash(2800).academics(60)
                        .didEverything()::build)));
        list.add(new Scenario("CRACKED PHONE", "A cracked screen - repair it in the Shop", WoodButton.PURPLE,
                () -> play(new Setup().at(15, TimeSlot.MORNING).cash(2600).phoneBroken()::build)));
        list.add(new Scenario("LOAD GAME SCREEN", "Main menu, with a few test saves to load", WoodButton.TAN,
                this::openWithTestSaves));
        list.add(new Scenario("NEW GAME", "The whole game from the start, with test files", WoodButton.GREEN,
                this::openMenu));
        list.add(new Scenario("AUTO-PLAY: RECKLESS", "Never eats or showers, pays for everything",
                WoodButton.DARK_WOOD, () -> autoPlay("Reckless Month", false)));
        list.add(new Scenario("AUTO-PLAY: STRESSED OUT", "Never rests, always works overtime",
                WoodButton.DARK_WOOD, () -> autoPlay("Stressed-Out Month", true)));
        return list;
    }

    private void play(Supplier<GameEngine> situation) {
        GameEngine engine = situation.get();
        frame.dispose();
        GameApp app = GameApp.forTesting();
        app.start();
        app.playFrom(engine);
    }

    private void openMenu() {
        frame.dispose();
        GameApp.forTesting().start();
    }

    private void openWithTestSaves() {
        SaveManager saves = new SaveManager(Paths.get("saves_test.csv"));
        try {
            saves.deleteAll();
            int[][] points = {{3, 0, 4200}, {3, 1, 4130}, {7, 2, 3900}, {12, 0, 3100}, {18, 1, 2400}};
            for (int[] point : points) {
                GameEngine engine = new Setup().at(point[0], TimeSlot.values()[point[1]]).cash(point[2]).build();
                saves.saveCheckpoint(engine.captureSnapshot());
            }
        } catch (IOException e) {
            GameDialog.showMessage(frame, "Test Saves", "Couldn't write saves_test.csv:\n" + e.getMessage(),
                    GameDialog.Tone.ERROR);
            return;
        }
        openMenu();
    }

    private static final class Setup {
        private int day = 2;
        private TimeSlot slot = TimeSlot.MORNING;
        private int cash = 3000;
        private int hunger = 80;
        private int stress = 20;
        private int academics = 75;
        private int sickness = 10;
        private int medicine = 3;
        private int water = 300;
        private boolean rainyToday;
        private boolean eventHandled = true;
        private boolean phoneBroken;
        private boolean ate;
        private boolean studied;
        private boolean showered;

        Setup at(int day, TimeSlot slot) {
            this.day = day;
            this.slot = slot;
            return this;
        }

        Setup cash(int cash) {
            this.cash = cash;
            return this;
        }

        Setup hunger(int hunger) {
            this.hunger = hunger;
            return this;
        }

        Setup stress(int stress) {
            this.stress = stress;
            return this;
        }

        Setup academics(int academics) {
            this.academics = academics;
            return this;
        }

        Setup medicine(int pills) {
            this.medicine = pills;
            return this;
        }

        Setup water(int bill) {
            this.water = bill;
            return this;
        }

        Setup rainyToday() {
            this.rainyToday = true;
            return this;
        }

        Setup eventWaiting() {
            this.eventHandled = false;
            return this;
        }

        Setup phoneBroken() {
            this.phoneBroken = true;
            return this;
        }

        Setup studied() {
            this.studied = true;
            return this;
        }

        Setup showered() {
            this.showered = true;
            return this;
        }

        Setup didEverything() {
            this.ate = true;
            this.studied = true;
            this.showered = true;
            return this;
        }

        GameEngine build() {
            TreeSet<Integer> rain = new TreeSet<>(Arrays.asList(3, 5, 8, 11, 17, 19, 22, 24, 26, 27));
            if (rainyToday) {
                rain.add(day);
            } else {
                rain.remove(day);
            }
            GameSnapshot snapshot = new GameSnapshot(day, slot, ApartmentType.STANDARD,
                    rainyToday ? Weather.RAINY : Weather.CLEAR, water, cash, hunger, stress, academics, sickness,
                    medicine, 0, false, ate, false, 0, null, new ArrayList<>(), eventHandled, false, cash,
                    new EnumMap<>(CashCategory.class), new ArrayList<>(), phoneBroken, studied, showered,
                    rain, new ArrayList<>());
            return GameEngine.fromSnapshot(snapshot);
        }
    }

    private static final class AutoPlayResult {
        final GameEngine engine;
        final List<String> report;
        final String ending;

        AutoPlayResult(GameEngine engine, List<String> report, String ending) {
            this.engine = engine;
            this.report = report;
            this.ending = ending;
        }
    }

    private void autoPlay(String title, boolean stressedOut) {
        AutoPlayResult result = simulate(stressedOut);
        List<String> shown = new ArrayList<>(result.report);
        if (shown.size() > 18) {
            int more = shown.size() - 17;
            shown = new ArrayList<>(shown.subList(0, 17));
            shown.add("...and " + more + " more");
        }
        shown.add(result.ending);
        if (result.engine.isMonthComplete()) {
            GameDialog.showMessage(frame, title, String.join("\n", shown));
            return;
        }
        int choice = GameDialog.choose(frame, title, String.join("\n", shown),
                new String[] {"SHOW WHERE IT ENDED"}, new Color[] {WoodButton.GREEN}, "BACK");
        if (choice == 0) {
            play(() -> result.engine);
        }
    }

    private static AutoPlayResult simulate(boolean stressedOut) {
        GameEngine engine = new GameEngine();
        engine.chooseApartment(ApartmentType.STANDARD);
        List<String> report = new ArrayList<>();
        Player player = engine.getState().getPlayer();
        boolean stressNoted = false;
        boolean lockNoted = false;
        boolean blockNoted = false;

        for (int guard = 0; guard < 200 && !engine.isGameOver() && !engine.isMonthComplete(); guard++) {
            GameState state = engine.getState();
            int day = state.getDay();
            TimeSlot slot = state.getTimeSlot();
            int bills = player.getMedicalBillCount();

            if (engine.isRandomEventPendingToday()) {
                RandomEvent event = engine.triggerRandomEventIfDue();
                if (event != null) {
                    boolean accept = stressedOut ? event instanceof AcademicCommissionEvent : true;
                    engine.resolveActiveRandomEvent(accept);
                    report.add("Day " + day + ": " + event.getName() + " - "
                            + (accept ? event.getAcceptLabel() : event.getDeclineLabel()).toLowerCase());
                }
            }

            String blocked = null;
            if (engine.getCurrentDayType() == DayType.STANDARD_TUTORIAL) {
                engine.performTask(engine.getTutorialTaskFor(slot));
            } else if (engine.isPartTimeSlotNow() && !player.isPartTimeLocked()) {
                PickpocketedEvent pickpocketed = engine.workPartTime(ShiftType.OVERTIME);
                if (pickpocketed != null) {
                    report.add("Day " + day + ": worked at 100% Stress - pickpocketed on the jeepney ("
                            + pickpocketed.getAcceptEffects() + ")");
                }
            } else {
                Task task = stressedOut ? stressedOutTask(slot) : recklessTask(slot);
                if (task instanceof HygieneTask && engine.isTodayRainy()) {
                    engine.performRainyDayLaundry(new RainyDayLaundryEvent(), true);
                } else if (!engine.performTask(task) && player.isIncapacitatedByStress()) {
                    blocked = task.getName();
                }
            }
            if (blocked != null && !blockNoted) {
                blockNoted = true;
                report.add("Day " + day + ": too stressed to " + blocked.toLowerCase()
                        + " - Eat, Hygiene and Study fail at 100% Stress");
            }

            if (slot == TimeSlot.NIGHT) {
                engine.markNightActionUsed();
                engine.endDay();
            } else {
                engine.advanceTimeSlot();
            }

            if (player.getMedicalBillCount() > bills) {
                report.add("Day " + engine.getState().getDay() + ": Medical Bill -" + P + "500 ("
                        + player.getLastMedicalBillReason() + ")");
            }
            if (!stressNoted && player.getStress().getValue() >= 100) {
                stressNoted = true;
                report.add("Day " + engine.getState().getDay() + ": Stress hit 100%");
            }
            if (!lockNoted && player.isPartTimeLocked()) {
                lockNoted = true;
                report.add("Day " + engine.getState().getDay() + ": Academics fell to "
                        + player.getAcademic().getValue() + "% - the part-time job is locked");
            }
        }

        int cash = player.getCash();
        String ending;
        if (engine.isMonthComplete()) {
            ending = "It survived the month with " + P + String.format("%,d", cash) + " left!";
        } else if (engine.isStranded()) {
            ending = "Day 30: stranded - " + P + String.format("%,d", cash) + " isn't enough for the fare home.";
        } else {
            ending = "Day " + engine.getState().getDay() + ": went broke (" + (cash < 0 ? "-" : "") + P
                    + String.format("%,d", Math.abs(cash)) + ").";
        }
        return new AutoPlayResult(engine, report, ending);
    }

    private static Task recklessTask(TimeSlot slot) {
        return slot == TimeSlot.AFTERNOON ? new StudyTask() : new WalkTask();
    }

    private static Task stressedOutTask(TimeSlot slot) {
        switch (slot) {
            case MORNING:
                return new EatTask();
            case AFTERNOON:
                return new StudyTask();
            default:
                return new HygieneTask();
        }
    }
}
