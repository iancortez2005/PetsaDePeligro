package petsa.ui;

import petsa.model.GameEngine;
import petsa.model.Player;
import petsa.model.Task;
import petsa.model.Task.EatTask;
import petsa.model.Task.HygieneTask;
import petsa.model.Timeline.DayType;
import petsa.model.Timeline.TimeSlot;
import petsa.ui.PixelKit.TextBlock;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.util.List;
import java.util.Random;

/**
 * The Board's task picker, shown over the room: a corkboard with one
 * pinned sticky note per task (Eat, Hygiene, Study). Each note says
 * what the task does, what it costs, and the attribute's current
 * value; clicking a note picks that task for the current part of the
 * day. A paper strip underneath explains anything special about right
 * now: Day 1's guided task, Stress at 100%, rain, or a part-time shift
 * being available tonight.
 *
 * Notes that can't be picked right now are greyed out with the reason
 * (on Day 1 only the guided task is open; at 100% Stress none are).
 *
 * While shown it swallows every mouse click, so nothing in the room can
 * be clicked until a task is picked or the player goes back.
 */
public class BoardOverlay extends JPanel {

    /** The three Board tasks. */
    public enum Choice { EAT, HYGIENE, STUDY }

    /** What picking a note or going back does - supplied by RoomPanel. */
    public interface Actions {
        void onChoose(Choice choice);

        void onClose();
    }

    // Declared here on purpose: inside a Swing component a bare WIDTH/HEIGHT would otherwise mean
    // ImageObserver.WIDTH/HEIGHT (1 and 2), inherited from java.awt.Component.
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    // Layout - multiples of PixelKit.SCALE so every edge lands on the pixel grid.
    private static final Rectangle FRAME = new Rectangle(180, 45, 921, 630);
    private static final Rectangle TITLE_STRIP = new Rectangle(423, 66, 435, 63);
    private static final int NOTE_Y = 150;
    private static final int NOTE_W = 255;
    private static final int NOTE_H = 300;
    private static final int[] NOTE_X = {237, 513, 789};
    private static final Rectangle HINT_STRIP = new Rectangle(237, 471, 807, 78);
    private static final Rectangle BACK_BUTTON = new Rectangle(543, 573, 195, 48);

    private static final Color VEIL = new Color(0, 0, 0, 150);
    private static final Color FRAME_WOOD = new Color(0x6E, 0x4A, 0x2A);
    private static final Color FRAME_EDGE = new Color(0x3A, 0x24, 0x12);
    private static final Color CORK = new Color(0xB8, 0x86, 0x4E);
    private static final Color CORK_DARK = new Color(0x9A, 0x6C, 0x3A);
    private static final Color PAPER = new Color(0xF6, 0xF4, 0xEE);
    private static final Color INK = new Color(0x2E, 0x24, 0x1C);
    private static final Color DIM = new Color(0x6E, 0x60, 0x50);
    private static final Color NOTE_YELLOW = new Color(0xF0, 0xD6, 0x7C);
    private static final Color NOTE_PINK = new Color(0xF0, 0xAC, 0xB2);
    private static final Color NOTE_BLUE = new Color(0xA2, 0xC6, 0xE8);

    private final GameEngine engine;
    private final Actions actions;
    private String title = "";
    private String hint = "";

    public BoardOverlay(GameEngine engine, Actions actions) {
        this.engine = engine;
        this.actions = actions;
        setLayout(null);
        setOpaque(false);
        setSize(WIDTH, HEIGHT);
        MouseAdapter swallowEverything = new MouseAdapter() { };
        addMouseListener(swallowEverything);
        addMouseMotionListener(swallowEverything);
        addMouseWheelListener(swallowEverything);
    }

    /** Rebuilds the notes and hint from the current game state. Call just before showing. */
    public void refresh() {
        removeAll();
        Player player = engine.getState().getPlayer();
        TimeSlot slot = engine.getState().getTimeSlot();
        boolean guided = engine.getCurrentDayType() == DayType.STANDARD_TUTORIAL;
        Choice guidedChoice = guided ? choiceOf(engine.getTutorialTaskFor(slot)) : null;
        boolean stressed = player.isIncapacitatedByStress();
        boolean rainy = engine.isTodayRainy();

        title = slot.getLabel().toUpperCase() + " TASK";
        hint = hintFor(slot, guided, guidedChoice, stressed, rainy, player);

        int foodStock = player.getInventory().getFoodStock();
        String eatCost = foodStock > 0
                ? "Uses 1 day of bulk food (" + foodStock + " left)."
                : "Costs \u20B1" + EatTask.MEAL_COST + ".";
        addNote(0, Choice.EAT, "EAT", NOTE_YELLOW,
                new String[] {"Refills Hunger.", eatCost, "Skip it all day: -Hunger."},
                "Hunger now: " + player.getHunger().getValue() + "%",
                blockedReason(Choice.EAT, guidedChoice, stressed, engine.hasEatenToday()));
        addNote(1, Choice.HYGIENE, "HYGIENE", NOTE_PINK,
                rainy
                        ? new String[] {"Rain! Showering means a trip to the laundromat instead."}
                        : new String[] {"Lowers Sickness.", "Adds to the water bill (paid on Day 29).",
                                "Skip it all day: +Sickness."},
                "Sickness now: " + player.getSickness().getValue() + "%",
                blockedReason(Choice.HYGIENE, guidedChoice, stressed, engine.hasDoneHygieneToday()));
        addNote(2, Choice.STUDY, "STUDY", NOTE_BLUE,
                new String[] {"Raises Academics.", "Adds a little Stress.", "Skip it all day: -Academics."},
                "Academics now: " + player.getAcademic().getValue() + "%",
                blockedReason(Choice.STUDY, guidedChoice, stressed, engine.hasStudiedToday()));

        WoodButton back = new WoodButton("< BACK", null, WoodButton.TAN);
        back.setBounds(BACK_BUTTON);
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> actions.onClose());
        add(back);

        TextBlock hintText = new TextBlock(hint, 9f, INK, HINT_STRIP.width - 48, 16, true);
        hintText.setLocation(HINT_STRIP.x + 24, HINT_STRIP.y + (HINT_STRIP.height - hintText.getBlockHeight()) / 2);
        add(hintText);

        revalidate();
        repaint();
    }

    /** Why a note can't be picked right now, or null if it can. */
    private static String blockedReason(Choice choice, Choice guidedChoice, boolean stressed, boolean doneToday) {
        if (doneToday) {
            return "DONE TODAY"; // each task once a day
        }
        if (stressed) {
            return "TOO STRESSED";
        }
        if (guidedChoice != null && choice != guidedChoice) {
            return "NOT TODAY";
        }
        return null;
    }

    private String hintFor(TimeSlot slot, boolean guided, Choice guidedChoice, boolean stressed, boolean rainy,
            Player player) {
        if (stressed) {
            return "Stress is at 100% - you can't eat, shower or study today. "
                    + "Take a walk at the Door to calm down.";
        }
        if (guided) {
            return "Guided day: this " + slot.getLabel() + "'s task is " + displayName(guidedChoice) + ". "
                    + reasonFor(guidedChoice) + " From tomorrow, every choice is yours.";
        }
        if (engine.hasEatenToday() && engine.hasStudiedToday() && engine.hasDoneHygieneToday()) {
            return "You've done all three tasks today. Take a walk at the Door, or sleep once it's Night.";
        }
        String extra = "";
        if (engine.isPartTimeSlotNow() && !player.isPartTimeLocked()) {
            extra = " Tonight you could also work a part-time shift at the Door instead.";
        } else if (rainy) {
            extra = " It's raining, so Hygiene means the laundromat today.";
        }
        return "Pick one task for the " + slot.getLabel() + ". Each task can be done once a day." + extra;
    }

    private static String reasonFor(Choice choice) {
        switch (choice) {
            case HYGIENE:
                return "A shower keeps Sickness down.";
            case EAT:
                return "Eating refills Hunger.";
            default:
                return "Studying raises Academics.";
        }
    }

    private static String displayName(Choice choice) {
        switch (choice) {
            case HYGIENE:
                return "Hygiene";
            case EAT:
                return "Eat";
            default:
                return "Study";
        }
    }

    private static Choice choiceOf(Task task) {
        if (task instanceof EatTask) {
            return Choice.EAT;
        }
        if (task instanceof HygieneTask) {
            return Choice.HYGIENE;
        }
        return Choice.STUDY;
    }

    private void addNote(int index, Choice choice, String name, Color color, String[] lines, String readout,
            String blockedReason) {
        NoteButton note = new NoteButton(name, color, lines, readout, blockedReason, index);
        note.setBounds(NOTE_X[index], NOTE_Y, NOTE_W, NOTE_H);
        note.addActionListener(e -> actions.onChoose(choice));
        add(note);
    }

    // ------------------------------------------------------------------
    // Painting: veil, wooden frame, cork, title strip, hint strip
    // ------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(VEIL);
        g2.fillRect(0, 0, getWidth(), getHeight());
        PixelKit.paint(g2, 0, 0, getWidth(), getHeight(), this::paintBoard);

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        Font titleFont = PixelKit.font(16f);
        FontMetrics fm = g2.getFontMetrics(titleFont);
        g2.setFont(titleFont);
        g2.setColor(INK);
        String heading = "DAILY TASKS";
        g2.drawString(heading, TITLE_STRIP.x + (TITLE_STRIP.width - fm.stringWidth(heading)) / 2, TITLE_STRIP.y + 27);
        Font slotFont = PixelKit.font(8f);
        g2.setFont(slotFont);
        g2.setColor(DIM);
        FontMetrics sfm = g2.getFontMetrics(slotFont);
        g2.drawString(title, TITLE_STRIP.x + (TITLE_STRIP.width - sfm.stringWidth(title)) / 2, TITLE_STRIP.y + 48);
        g2.dispose();
    }

    private void paintBoard(Graphics2D pg) {
        int px = PixelKit.SCALE;
        pg.setColor(new Color(0, 0, 0, 110));
        pg.fillRect(FRAME.x + 12, FRAME.y + 12, FRAME.width, FRAME.height);
        pg.setColor(FRAME_EDGE);
        pg.fillRect(FRAME.x, FRAME.y, FRAME.width, FRAME.height);
        pg.setColor(FRAME_WOOD);
        pg.fillRect(FRAME.x + px, FRAME.y + px, FRAME.width - px * 2, FRAME.height - px * 2);
        pg.setColor(FRAME_EDGE);
        pg.fillRect(FRAME.x + 15, FRAME.y + 15, FRAME.width - 30, FRAME.height - 30);
        pg.setColor(CORK);
        pg.fillRect(FRAME.x + 18, FRAME.y + 18, FRAME.width - 36, FRAME.height - 36);

        // Cork speckles (a fixed seed, so they don't jump around on every repaint).
        Random speckles = new Random(29);
        pg.setColor(CORK_DARK);
        for (int i = 0; i < 260; i++) {
            int x = FRAME.x + 21 + speckles.nextInt((FRAME.width - 42) / px) * px;
            int y = FRAME.y + 21 + speckles.nextInt((FRAME.height - 42) / px) * px;
            pg.fillRect(x, y, px, px);
        }

        paintPaperStrip(pg, TITLE_STRIP);
        paintPaperStrip(pg, HINT_STRIP);
    }

    private void paintPaperStrip(Graphics2D pg, Rectangle r) {
        int px = PixelKit.SCALE;
        pg.setColor(new Color(0, 0, 0, 90));
        pg.fillRect(r.x + 6, r.y + 6, r.width, r.height);
        pg.setColor(PAPER);
        pg.fillRect(r.x, r.y, r.width, r.height);
        pg.setColor(new Color(0xC9, 0xB8, 0x8E));
        pg.fillRect(r.x, r.y + r.height - px, r.width, px);
        paintPin(pg, r.x + r.width / 2 - 6, r.y - 3, new Color(0xB0, 0x3A, 0x2E));
    }

    static void paintPin(Graphics2D pg, int x, int y, Color color) {
        pg.setColor(new Color(0, 0, 0, 90));
        pg.fillOval(x + 3, y + 3, 15, 15);
        pg.setColor(color);
        pg.fillOval(x, y, 15, 15);
        pg.setColor(new Color(255, 255, 255, 120));
        pg.fillRect(x + 3, y + 3, 3, 3);
    }

    // ------------------------------------------------------------------
    // One sticky note
    // ------------------------------------------------------------------

    /** A pinned sticky note that is also a button. Greyed out (and unclickable) when blockedReason is set. */
    private static class NoteButton extends JButton {
        private static final int PADDING = 18;

        private final String name;
        private final Color color;
        private final List<String> lines;
        private final String readout;
        private final String blockedReason;
        private final double tilt;

        NoteButton(String name, Color color, String[] textLines, String readout, String blockedReason, int index) {
            this.name = name;
            this.color = color;
            this.readout = readout;
            this.blockedReason = blockedReason;
            this.tilt = (index - 1) * 1.5; // a slight, varied tilt so the notes look hand-pinned
            Font bodyFont = PixelKit.font(10f);
            this.lines = PixelKit.wrapText(String.join("\n", textLines), bodyFont, NOTE_W - PADDING * 2 - 6);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setEnabled(blockedReason == null);
            setCursor(Cursor.getPredefinedCursor(blockedReason == null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            boolean enabled = isEnabled();
            boolean hover = enabled && getModel().isRollover();
            boolean pressed = enabled && getModel().isPressed();
            Color paper = !enabled ? mix(color, new Color(0x9A, 0x94, 0x8C), 0.65f)
                    : pressed ? shade(color, 0.9f) : hover ? shade(color, 1.08f) : color;
            int lift = hover && !pressed ? 3 : 0;

            g2.rotate(Math.toRadians(tilt), w / 2.0, h / 2.0);
            PixelKit.paint(g2, 0, 0, w, h, pg -> {
                int px = PixelKit.SCALE;
                pg.setColor(new Color(0, 0, 0, 100));
                pg.fillRect(9, 12, w - 15, h - 15);
                pg.setColor(shade(paper, 0.8f));
                pg.fillRect(3, 3 - lift, w - 15, h - 15);
                pg.setColor(paper);
                pg.fillRect(3, 3 - lift, w - 15 - px, h - 15 - px);
                // Folded bottom-right corner.
                pg.setColor(shade(paper, 0.75f));
                pg.fillRect(w - 36, h - 36 - lift, 24, 24);
                paintPin(pg, w / 2 - 12, 6 - lift, new Color(0x3A, 0x6E, 0xB0));
            });

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color ink = enabled ? INK : new Color(0x5A, 0x54, 0x4C);
            Font titleFont = PixelKit.font(16f);
            g2.setColor(ink);
            PixelKit.drawText(g2, name, titleFont, (w - 12 - PixelKit.textWidth(name, titleFont)) / 2, 58 - lift);

            Font bodyFont = PixelKit.font(10f);
            int y = 96 - lift;
            for (String line : lines) {
                g2.setColor(ink);
                PixelKit.drawText(g2, line, bodyFont, PADDING, y);
                y += 19;
            }

            Font readoutFont = PixelKit.font(9f);
            g2.setColor(enabled ? DIM : ink);
            PixelKit.drawText(g2, readout, readoutFont, PADDING, h - 44 - lift);

            if (blockedReason != null) {
                Font blockedFont = PixelKit.font(12f);
                g2.setColor(new Color(0x8A, 0x2A, 0x20));
                PixelKit.drawText(g2, blockedReason, blockedFont,
                        (w - 12 - PixelKit.textWidth(blockedReason, blockedFont)) / 2, h - 74); // below the text, above the readout
            }
            g2.dispose();
        }

        private static Color shade(Color c, float factor) {
            return new Color(clamp(Math.round(c.getRed() * factor)), clamp(Math.round(c.getGreen() * factor)),
                    clamp(Math.round(c.getBlue() * factor)), c.getAlpha());
        }

        private static Color mix(Color a, Color b, float amount) {
            return new Color(clamp(Math.round(a.getRed() + (b.getRed() - a.getRed()) * amount)),
                    clamp(Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * amount)),
                    clamp(Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * amount)));
        }

        private static int clamp(int v) {
            return Math.max(0, Math.min(255, v));
        }
    }
}
