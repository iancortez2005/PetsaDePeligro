package petsa.ui;

import petsa.model.SaveManager.GameSnapshot;
import petsa.model.Timeline.TimeSlot;
import petsa.ui.PixelKit.BackgroundPanel;
import petsa.ui.PixelKit.FadePane;
import petsa.ui.PixelKit.MoneyLabel;
import petsa.ui.PixelKit.PaperCard;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Load Game screen, modeled on the reference mockup: a scrolling
 * row of day cards, one per day that has at least one saved
 * checkpoint, each showing the day, a calendar, three pips for which
 * time phases (Morning / Mid-day / Night) are saved, and the balance
 * of that day's latest save. Clicking a card opens a clipboard where
 * the player picks the time phase to load; phases with no save are
 * greyed out and can't be clicked.
 *
 * Checkpoints come from SaveManager - there is one per (day, time
 * phase), written whenever the player uses SAVE & EXIT on the pause
 * screen.
 *
 * Optional background: src/petsa/ui/resources/load_background.jpeg
 * (falls back to the menu background).
 */
public class LoadGamePanel extends BackgroundPanel {

    /** What the screen asks the window to do. */
    public interface Listener {
        /** Load this checkpoint. Return false if it didn't happen (e.g. the player cancelled), so the screen stays usable. */
        boolean onLoad(GameSnapshot snapshot);

        void onBack();

        void onNewGame();
    }

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final int CARD_W = 180;
    private static final int CARD_H = 246;
    private static final int CARD_GAP = 18;
    private static final int ROW_PADDING = 12;

    private static final Color INK = new Color(0x2E, 0x24, 0x1C);
    private static final Color GOLD = new Color(0xE8, 0xC2, 0x4A);
    private static final Color CREAM_TEXT = new Color(0xE8, 0xDD, 0xC0);
    private static final Color BALANCE_GREEN = new Color(0x3E, 0x8A, 0x4E);
    private static final Color CARD_PAPER = new Color(0xEE, 0xE0, 0xC2);
    private static final Color CARD_HOVER = new Color(0xF8, 0xEE, 0xD6);
    private static final Color CARD_PRESSED = new Color(0xDC, 0xCC, 0xAC);
    private static final Color CARD_EDGE = new Color(0x8A, 0x74, 0x58);
    private static final Color PIP_EMPTY = new Color(0xCF, 0xC3, 0xAE);

    private final Listener listener;
    private final PhasePicker picker = new PhasePicker();

    /** checkpoints: every saved checkpoint, in any order. */
    public LoadGamePanel(List<GameSnapshot> checkpoints, Listener listener) {
        super("resources/load_background.jpeg", "resources/load_background.png",
                "resources/menu_background.jpeg", "resources/menu_background.png");
        this.listener = listener;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));

        JLabel title = new JLabel("LOAD GAME", SwingConstants.CENTER);
        title.setFont(PixelKit.font(40f));
        title.setForeground(GOLD);
        title.setBounds(0, 40, WIDTH, 56);
        add(title);

        JLabel subtitle = new JLabel("SELECT DAY TO CONTINUE OR START A NEW GAME", SwingConstants.CENTER);
        subtitle.setFont(PixelKit.font(16f));
        subtitle.setForeground(CREAM_TEXT);
        subtitle.setBounds(0, 112, WIDTH, 26);
        add(subtitle);

        Map<Integer, Map<TimeSlot, GameSnapshot>> byDay = groupByDay(checkpoints);
        if (byDay.isEmpty()) {
            add(buildEmptyNote());
        } else {
            add(buildCardStrip(byDay));
        }

        WoodButton backButton = new WoodButton("< BACK TO MAIN MENU", null, WoodButton.BLUE);
        backButton.setBounds(292, 604, 340, 64);
        backButton.addActionListener(e -> listener.onBack());
        add(backButton);

        WoodButton newGameButton = new WoodButton("NEW GAME", null, WoodButton.GREEN);
        newGameButton.setBounds(648, 604, 340, 64);
        newGameButton.addActionListener(e -> listener.onNewGame());
        add(newGameButton);

        add(picker, 0); // index 0 = drawn on top of everything else
        installEscapeKey();
    }

    /** Overlapping children (the phase picker covers the cards), so let Swing repaint them in the right order. */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    /** Groups checkpoints as day -> (time phase -> checkpoint), days in ascending order. */
    private static Map<Integer, Map<TimeSlot, GameSnapshot>> groupByDay(List<GameSnapshot> checkpoints) {
        Map<Integer, Map<TimeSlot, GameSnapshot>> byDay = new TreeMap<>();
        for (GameSnapshot snapshot : checkpoints) {
            byDay.computeIfAbsent(snapshot.getDay(), d -> new EnumMap<>(TimeSlot.class))
                    .put(snapshot.getTimeSlot(), snapshot);
        }
        return byDay;
    }

    private JComponent buildEmptyNote() {
        PaperCard note = new PaperCard(PaperCard.CREAM, false);
        note.setBounds(330, 230, 620, 190);
        String[] lines = {"NO SAVED GAMES YET", "", "Pause during a game and choose", "SAVE & EXIT TO TITLE to save."};
        int y = 34;
        for (int i = 0; i < lines.length; i++) {
            JLabel line = new JLabel(lines[i], SwingConstants.CENTER);
            line.setFont(PixelKit.font(i == 0 ? 16f : 10f));
            line.setForeground(INK);
            line.setBounds(0, y, 614, 26);
            note.add(line);
            y += (i == 0) ? 28 : 30;
        }
        return note;
    }

    private JComponent buildCardStrip(Map<Integer, Map<TimeSlot, GameSnapshot>> byDay) {
        CardRow row = new CardRow();
        for (Map.Entry<Integer, Map<TimeSlot, GameSnapshot>> entry : byDay.entrySet()) {
            DayCard card = new DayCard(entry.getKey(), entry.getValue());
            card.addActionListener(e -> FadePane.run(this, () -> picker.open(card.day, card.saves)));
            row.add(card);
        }
        row.setPreferredSize(new Dimension(
                byDay.size() * (CARD_W + CARD_GAP) + CARD_GAP, CARD_H + ROW_PADDING * 2));

        JScrollPane scroll = new JScrollPane(row,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getHorizontalScrollBar().setUI(new PixelScrollBarUI());
        scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 18));
        scroll.getHorizontalScrollBar().setOpaque(false);
        scroll.getHorizontalScrollBar().setUnitIncrement(30);
        scroll.setBounds(60, 168, WIDTH - 120, CARD_H + ROW_PADDING * 2 + 22);
        return scroll;
    }

    /** Esc closes the phase picker if it's open. */
    private void installEscapeKey() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closePicker");
        getActionMap().put("closePicker", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (picker.isVisible()) {
                    FadePane.run(LoadGamePanel.this, picker::close);
                }
            }
        });
    }

    /** Label for a time phase, matching the room's time bar. */
    private static Color phaseColor(TimeSlot slot) {
        switch (slot) {
            case AFTERNOON:
                return WoodButton.YELLOW;
            case NIGHT:
                return WoodButton.PURPLE;
            default:
                return WoodButton.ORANGE;
        }
    }

    // ------------------------------------------------------------------
    // The horizontal row of cards
    // ------------------------------------------------------------------

    /**
     * Holds the day cards in one centered row. When there are fewer cards
     * than fit on screen it stretches to the viewport width (so they
     * center); otherwise it keeps its full width and the scrollbar appears.
     */
    private static class CardRow extends JPanel implements Scrollable {
        CardRow() {
            super(new FlowLayout(FlowLayout.CENTER, CARD_GAP, ROW_PADDING));
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 30;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(CARD_W + CARD_GAP, visibleRect.width - CARD_W);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport && getParent().getWidth() > getPreferredSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // One day card
    // ------------------------------------------------------------------

    private static class DayCard extends JButton {
        private static final int SHADOW = 6;

        final int day;
        final Map<TimeSlot, GameSnapshot> saves;
        private final boolean lost; // the day's latest save is where the player went broke

        DayCard(int day, Map<TimeSlot, GameSnapshot> saves) {
            this.day = day;
            this.saves = saves;
            setLayout(null);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(CARD_W, CARD_H));

            GameSnapshot latest = latestSave();
            lost = latest.getCash() <= 0;
            MoneyLabel balance = new MoneyLabel(12f, lost ? WoodButton.RED : BALANCE_GREEN,
                    MoneyLabel.Align.CENTER);
            balance.setAmount(latest.getCash());
            balance.setBounds(0, CARD_H - SHADOW - 52, CARD_W - SHADOW, 30);
            add(balance);

            StringBuilder tip = new StringBuilder("Saved: ");
            for (TimeSlot slot : saves.keySet()) {
                tip.append(tip.length() > 7 ? ", " : "").append(slot.getLabel().toUpperCase(Locale.ROOT));
            }
            if (lost) {
                tip.append(" - the day you went broke");
            }
            setToolTipText(tip.toString());
        }

        /** The day's latest save (Night, else Mid-day, else Morning) - its balance is the one shown. */
        private GameSnapshot latestSave() {
            TimeSlot[] order = TimeSlot.values();
            for (int i = order.length - 1; i >= 0; i--) {
                if (saves.containsKey(order[i])) {
                    return saves.get(order[i]);
                }
            }
            throw new IllegalStateException("A day card needs at least one save");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            int paperW = w - SHADOW;
            int paperH = h - SHADOW;
            Color paper = getModel().isPressed() ? CARD_PRESSED : (getModel().isRollover() ? CARD_HOVER : CARD_PAPER);

            PixelKit.paint(g2, 0, 0, w, h, pg -> {
                pg.setColor(new Color(0, 0, 0, 100));
                pg.fillRect(SHADOW, SHADOW, paperW, paperH);
                pg.setColor(paper);
                pg.fillRect(0, 0, paperW, paperH);
                pg.setStroke(new BasicStroke(PixelKit.SCALE));
                pg.setColor(CARD_EDGE);
                pg.drawRect(0, 0, paperW - PixelKit.SCALE, paperH - PixelKit.SCALE);
                pg.drawRect(9, 9, paperW - 21, paperH - 21);
                paintCalendar(pg, paperW / 2, 84);
                paintPips(pg, paperW / 2, 176);
            });

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g2.setFont(PixelKit.font(16f));
            g2.setColor(INK);
            String text = "DAY " + day;
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (paperW - fm.stringWidth(text)) / 2, 48);
            if (lost) {
                g2.setFont(PixelKit.font(8f));
                g2.setColor(WoodButton.RED);
                String gameOver = "GAME OVER";
                g2.drawString(gameOver, (paperW - g2.getFontMetrics().stringWidth(gameOver)) / 2, 70);
            }
            g2.dispose();
        }

        /** A small calendar icon centered at (cx, top). */
        private void paintCalendar(Graphics2D pg, int cx, int top) {
            int w = 72;
            int h = 63;
            int x = cx - w / 2;
            pg.setColor(new Color(0xD8, 0xD2, 0xC6));
            pg.fillRect(x, top, w, h);
            pg.setColor(new Color(0xB0, 0x44, 0x3C));
            pg.fillRect(x, top, w, 15);
            pg.setColor(new Color(0x7A, 0x72, 0x66));
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 4; col++) {
                    pg.fillRect(x + 9 + col * 15, top + 24 + row * 12, 9, 6);
                }
            }
            pg.setColor(new Color(0x5E, 0x56, 0x4C));
            pg.drawRect(x, top, w - PixelKit.SCALE, h - PixelKit.SCALE);
        }

        /** Three pips - Morning, Mid-day, Night - filled in each phase's color if that phase is saved. */
        private void paintPips(Graphics2D pg, int cx, int top) {
            int size = 15;
            int gap = 12;
            int x = cx - (size * 3 + gap * 2) / 2;
            for (TimeSlot slot : TimeSlot.values()) {
                pg.setColor(saves.containsKey(slot) ? phaseColor(slot) : PIP_EMPTY);
                pg.fillRect(x, top, size, size);
                pg.setColor(CARD_EDGE);
                pg.drawRect(x, top, size - PixelKit.SCALE, size - PixelKit.SCALE);
                x += size + gap;
            }
        }
    }

    // ------------------------------------------------------------------
    // The "DAY n: CHOOSE TIME PHASE" clipboard
    // ------------------------------------------------------------------

    private class PhasePicker extends JPanel {
        private final Rectangle board = new Rectangle(420, 150, 441, 432); // multiples of PixelKit.SCALE
        private final JLabel title = new JLabel("", SwingConstants.CENTER);
        private final Map<TimeSlot, WoodButton> buttons = new EnumMap<>(TimeSlot.class);
        private Map<TimeSlot, GameSnapshot> saves = new EnumMap<>(TimeSlot.class);

        PhasePicker() {
            setLayout(null);
            setOpaque(false);
            // Qualified on purpose: inside a Swing component, a bare WIDTH/HEIGHT would mean
            // ImageObserver.WIDTH/HEIGHT (1 and 2), inherited from java.awt.Component.
            setBounds(0, 0, LoadGamePanel.WIDTH, LoadGamePanel.HEIGHT);
            setVisible(false);
            MouseAdapter swallowEverything = new MouseAdapter() { };
            addMouseListener(swallowEverything);
            addMouseMotionListener(swallowEverything);
            addMouseWheelListener(swallowEverything);

            title.setFont(PixelKit.font(16f));
            title.setForeground(GOLD);
            title.setBounds(board.x, board.y + 54, board.width, 26);
            add(title);

            int buttonX = board.x + 50;
            int buttonW = board.width - 100;
            int y = board.y + 104;
            for (TimeSlot slot : TimeSlot.values()) {
                WoodButton button = new WoodButton(slot.getLabel().toUpperCase(Locale.ROOT), null, phaseColor(slot));
                button.setBounds(buttonX, y, buttonW, 58);
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                button.addActionListener(e -> load(slot));
                buttons.put(slot, button);
                add(button);
                y += 72;
            }

            WoodButton cancel = new WoodButton("CANCEL", null, WoodButton.BLUE);
            cancel.setBounds(buttonX, y + 12, buttonW, 58);
            cancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cancel.addActionListener(e -> FadePane.run(LoadGamePanel.this, this::close));
            add(cancel);
        }

        /** Shows the picker for a day. Phases with no save are greyed out and can't be clicked. */
        void open(int day, Map<TimeSlot, GameSnapshot> daySaves) {
            this.saves = daySaves;
            title.setText("DAY " + day + ": CHOOSE TIME PHASE");
            for (Map.Entry<TimeSlot, WoodButton> entry : buttons.entrySet()) {
                boolean saved = daySaves.containsKey(entry.getKey());
                entry.getValue().setEnabled(saved);
                entry.getValue().setSubtitle(saved ? null : "Not saved");
            }
            setVisible(true);
            repaint();
        }

        void close() {
            setVisible(false);
        }

        private void load(TimeSlot slot) {
            GameSnapshot snapshot = saves.get(slot);
            if (snapshot == null) {
                return; // can't happen - the button is disabled - but never load a missing save
            }
            for (WoodButton button : buttons.values()) {
                button.setEnabled(false); // no double-loading while the screen fades out
            }
            if (!listener.onLoad(snapshot)) {
                // Not loaded after all - re-enable exactly the phases that have saves.
                for (Map.Entry<TimeSlot, WoodButton> entry : buttons.entrySet()) {
                    entry.getValue().setEnabled(saves.containsKey(entry.getKey()));
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(0, 0, 0, 140));
            g2.fillRect(0, 0, getWidth(), getHeight());
            PixelKit.paint(g2, 0, 0, getWidth(), getHeight(), this::paintClipboard);
            g2.dispose();
        }

        private void paintClipboard(Graphics2D pg) {
            int s = PixelKit.SCALE;
            pg.setColor(new Color(0, 0, 0, 110));
            pg.fillRect(board.x + 9, board.y + 12, board.width, board.height);
            pg.setColor(new Color(0x4A, 0x36, 0x26));
            pg.fillRect(board.x, board.y, board.width, board.height);
            pg.setStroke(new BasicStroke(s * 2));
            pg.setColor(new Color(0x2A, 0x1D, 0x12));
            pg.drawRect(board.x, board.y, board.width - s, board.height - s);
            pg.setStroke(new BasicStroke(s));
            pg.setColor(new Color(0x7A, 0x5A, 0x3A));
            pg.drawRect(board.x + 12, board.y + 12, board.width - 27, board.height - 27);

            // Metal clip at the top.
            int clipW = 150;
            int clipX = board.x + (board.width - clipW) / 2;
            pg.setColor(new Color(0x8C, 0x86, 0x7C));
            pg.fillRect(clipX, board.y - 21, clipW, 42);
            pg.setColor(new Color(0x4E, 0x4A, 0x44));
            pg.drawRect(clipX, board.y - 21, clipW - s, 42 - s);
            pg.fillRect(clipX + 24, board.y - 6, clipW - 48, 9);

            // Screws in the four corners.
            paintScrew(pg, board.x + 21, board.y + 21);
            paintScrew(pg, board.x + board.width - 39, board.y + 21);
            paintScrew(pg, board.x + 21, board.y + board.height - 39);
            paintScrew(pg, board.x + board.width - 39, board.y + board.height - 39);
        }

        private void paintScrew(Graphics2D pg, int x, int y) {
            pg.setColor(new Color(0x9A, 0x8A, 0x74));
            pg.fillOval(x, y, 18, 18);
            pg.setColor(new Color(0x3E, 0x30, 0x22));
            pg.drawOval(x, y, 18 - PixelKit.SCALE, 18 - PixelKit.SCALE);
            pg.drawLine(x + 5, y + 5, x + 12, y + 12);
            pg.drawLine(x + 12, y + 5, x + 5, y + 12);
        }
    }

    // ------------------------------------------------------------------
    // Pixel-style scrollbar
    // ------------------------------------------------------------------

    /** A flat, pixelated scrollbar (dark track, tan thumb, no arrow buttons) instead of the default Swing one. */
    private static class PixelScrollBarUI extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return invisibleButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return invisibleButton();
        }

        private static JButton invisibleButton() {
            JButton button = new JButton();
            Dimension zero = new Dimension(0, 0);
            button.setPreferredSize(zero);
            button.setMinimumSize(zero);
            button.setMaximumSize(zero);
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            PixelKit.paint((Graphics2D) g, r.x, r.y, r.width, r.height, pg -> {
                pg.setColor(new Color(20, 16, 14, 170));
                pg.fillRect(r.x, r.y, r.width, r.height);
            });
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) {
                return;
            }
            int px = PixelKit.SCALE;
            int artW = PixelKit.snap(r.width);
            int artH = PixelKit.snap(r.height);
            PixelKit.paint((Graphics2D) g, r.x, r.y, r.width, r.height, pg -> {
                pg.setColor(new Color(0x2A, 0x1D, 0x12));
                pg.fillRect(r.x, r.y, artW, artH);
                pg.setColor(isDragging ? new Color(0xD8, 0xB8, 0x86) : WoodButton.TAN);
                pg.fillRect(r.x + px, r.y + px, artW - px * 2, artH - px * 2);
            });
        }
    }
}
