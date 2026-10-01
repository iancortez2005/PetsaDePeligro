package petsa.ui;

import petsa.model.Timeline.TimeSlot;
import petsa.ui.PixelKit.MoneyLabel;
import petsa.ui.PixelKit.WoodButton;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;

/**
 * The shared look of the two "notebook on cardboard" screens shown over the
 * room: the room darkens, and a taped-down cardboard board appears with a
 * lined notebook page and an ink stamp at the top. Its two subclasses are
 * below: PauseOverlay ("GAME PAUSED") and GameOverOverlay ("GAME OVER").
 * They add their own rows of text (rowY() gives their positions) and big
 * buttons (addBigButton()).
 *
 * While shown it swallows every mouse click, so nothing underneath can be
 * clicked.
 */
abstract class NotebookOverlay extends JPanel {

    // Declared here on purpose: inside a Swing component a bare WIDTH/HEIGHT would otherwise mean
    // ImageObserver.WIDTH/HEIGHT (1 and 2), inherited from java.awt.Component.
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    // Positions and sizes are multiples of PixelKit.SCALE so every edge lands on the pixel grid.
    static final Rectangle BOARD = new Rectangle(300, 39, 681, 642);
    static final Rectangle PAPER = new Rectangle(366, 69, 549, 339);
    static final int MARGIN_X = PAPER.x + 58;       // red margin line on the notebook page
    static final int TEXT_X = MARGIN_X + 22;
    static final int RULE_SPACING = 30;
    static final int BUTTON_WIDTH = 470;

    static final Color VEIL = new Color(0, 0, 0, 150);
    static final Color CARDBOARD = new Color(0xB8, 0x92, 0x62);
    static final Color CARDBOARD_DARK = new Color(0x8E, 0x6C, 0x44);
    static final Color PAPER_WHITE = new Color(0xF6, 0xF4, 0xEE);
    static final Color RULE_BLUE = new Color(0x9C, 0xB6, 0xD6);
    static final Color MARGIN_RED = new Color(0xD8, 0x7A, 0x74);
    static final Color INK = new Color(0x2E, 0x24, 0x1C);
    static final Color STAMP_RED = new Color(0xB0, 0x3A, 0x2E);
    static final Color STAMP_GOLD = new Color(0xE8, 0xC2, 0x4A);
    static final Color BALANCE_GREEN = new Color(0x4E, 0x8A, 0x4A);
    static final Color TAPE = new Color(0xEF, 0xE3, 0xBE, 200);

    static final int STAMP_X = TEXT_X - 8;
    static final int STAMP_Y = PAPER.y + 36;
    static final int STAMP_W = PAPER.x + PAPER.width - STAMP_X - 24;
    static final int STAMP_H = 90;

    private final String stampText;
    private final Color stampFrameColor;
    private final Color stampTextColor;

    NotebookOverlay(String stampText, Color stampFrameColor, Color stampTextColor) {
        this.stampText = stampText;
        this.stampFrameColor = stampFrameColor;
        this.stampTextColor = stampTextColor;
        setLayout(null);
        setOpaque(false);
        setSize(WIDTH, HEIGHT);

        MouseAdapter swallowEverything = new MouseAdapter() { };
        addMouseListener(swallowEverything);
        addMouseMotionListener(swallowEverything);
        addMouseWheelListener(swallowEverything);
    }

    /** Top y of info row n (0, 1, 2), placed under the stamp and sitting on the page's ruled lines. */
    static int rowY(int row) {
        return PAPER.y + 158 + row * 56;
    }

    /** Adds a full-width row of 16pt text on the notebook page. */
    JLabel addRow(int row) {
        JLabel label = new JLabel();
        label.setFont(PixelKit.font(16f));
        label.setForeground(INK);
        label.setBounds(TEXT_X, rowY(row), PAPER.x + PAPER.width - TEXT_X - 20, 34);
        add(label);
        return label;
    }

    /** Adds one of the big buttons under the page; slot 0 is the upper one, slot 1 the lower one. */
    WoodButton addBigButton(String title, Color color, int slot, Runnable action) {
        WoodButton button = new WoodButton(title, null, color);
        button.setBounds(BOARD.x + (BOARD.width - BUTTON_WIDTH) / 2, 452 + slot * 100, BUTTON_WIDTH, 78);
        button.addActionListener(e -> action.run());
        add(button);
        return button;
    }

    /** Extra full-resolution text for a subclass (drawn last, on top). Default: nothing. */
    void paintExtraText(Graphics2D g2) {
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(VEIL);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // Everything decorative is pixelated to match the pixel-art style of the rest of the game.
        PixelKit.paint(g2, 0, 0, getWidth(), getHeight(), pg -> {
            paintBoard(pg);
            paintPaper(pg);
            paintStampBox(pg);
            paintTape(pg, BOARD.x - 18, BOARD.y + 26, -38);
            paintTape(pg, BOARD.x + BOARD.width - 70, BOARD.y + 12, 36);
            paintTape(pg, BOARD.x - 10, BOARD.y + BOARD.height - 60, 36);
            paintTape(pg, BOARD.x + BOARD.width - 78, BOARD.y + BOARD.height - 48, -36);
        });

        paintStampText(g2);
        paintExtraText(g2);
        g2.dispose();
    }

    void paintBoard(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 110));
        g2.fillRect(BOARD.x + 8, BOARD.y + 10, BOARD.width, BOARD.height);
        g2.setPaint(new GradientPaint(0, BOARD.y, CARDBOARD, 0, BOARD.y + BOARD.height, CARDBOARD_DARK));
        g2.fillRect(BOARD.x, BOARD.y, BOARD.width, BOARD.height);

        // A few faint vertical fibers so it reads as corrugated cardboard.
        g2.setColor(new Color(0x7A, 0x5A, 0x36, 45));
        for (int x = BOARD.x + 14; x < BOARD.x + BOARD.width; x += 22) {
            g2.drawLine(x, BOARD.y + 4, x, BOARD.y + BOARD.height - 4);
        }
        int px = PixelKit.SCALE;
        g2.setColor(new Color(0x5E, 0x44, 0x2A));
        g2.fillRect(BOARD.x, BOARD.y, BOARD.width, px);
        g2.fillRect(BOARD.x, BOARD.y + BOARD.height - px, BOARD.width, px);
        g2.fillRect(BOARD.x, BOARD.y, px, BOARD.height);
        g2.fillRect(BOARD.x + BOARD.width - px, BOARD.y, px, BOARD.height);
    }

    void paintPaper(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillRect(PAPER.x + 6, PAPER.y + 6, PAPER.width, PAPER.height);
        g2.setColor(PAPER_WHITE);
        g2.fillRect(PAPER.x, PAPER.y, PAPER.width, PAPER.height);

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(RULE_BLUE);
        for (int y = PAPER.y + 40; y < PAPER.y + PAPER.height - 8; y += RULE_SPACING) {
            g2.drawLine(PAPER.x + 4, y, PAPER.x + PAPER.width - 4, y);
        }
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(MARGIN_RED);
        g2.drawLine(MARGIN_X, PAPER.y, MARGIN_X, PAPER.y + PAPER.height);

        // Binder holes down the left edge.
        for (int y = PAPER.y + 28; y < PAPER.y + PAPER.height - 10; y += 44) {
            g2.setColor(new Color(0x6E, 0x52, 0x36));
            g2.fillOval(PAPER.x + 14, y, 16, 16);
            g2.setColor(new Color(0x3E, 0x2C, 0x1C));
            g2.drawOval(PAPER.x + 14, y, 16, 16);
        }

        // Paper clip at the top-left corner.
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(0x7A, 0x3E, 0x2C));
        g2.draw(new RoundRectangle2D.Float(PAPER.x + 70, PAPER.y - 26, 26, 78, 26, 26));
        g2.draw(new RoundRectangle2D.Float(PAPER.x + 77, PAPER.y - 14, 12, 52, 12, 12));
    }

    /** The stamp frame - a double border, like an ink stamp. */
    void paintStampBox(Graphics2D g2) {
        g2.setStroke(new BasicStroke(PixelKit.SCALE * 2));
        g2.setColor(stampFrameColor);
        g2.drawRect(STAMP_X, STAMP_Y, STAMP_W, STAMP_H);
        g2.setStroke(new BasicStroke(PixelKit.SCALE));
        g2.drawRect(STAMP_X + 12, STAMP_Y + 12, STAMP_W - 24, STAMP_H - 24);
    }

    /**
     * The stamp's words ("GAME PAUSED"), drawn at full size with smoothing off
     * so the pixel font stays crisp. 32pt is an exact multiple of the font's
     * 8px grid.
     */
    void paintStampText(Graphics2D g2) {
        String text = stampText;
        Font font = PixelKit.font(32f);
        FontMetrics fm = g2.getFontMetrics(font);
        while (fm.stringWidth(text) > STAMP_W - 48 && font.getSize2D() > 8f) {
            font = PixelKit.font(font.getSize2D() - 8f);
            fm = g2.getFontMetrics(font);
        }
        int textX = STAMP_X + (STAMP_W - fm.stringWidth(text)) / 2;
        int baseline = STAMP_Y + (STAMP_H + fm.getAscent() - fm.getDescent()) / 2;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2.setFont(font);
        g2.setColor(new Color(0x5A, 0x40, 0x20));
        g2.drawString(text, textX + 4, baseline + 4);
        g2.setColor(stampTextColor);
        g2.drawString(text, textX, baseline);
    }

    void paintTape(Graphics2D g2, int x, int y, double degrees) {
        AffineTransform saved = g2.getTransform();
        g2.rotate(Math.toRadians(degrees), x + 44, y + 15);
        g2.setColor(TAPE);
        g2.fillRect(x, y, 88, 30);
        g2.setColor(new Color(0xC9, 0xB8, 0x8E, 160));
        g2.drawRect(x, y, 88, 30);
        g2.setTransform(saved);
    }

    // ------------------------------------------------------------------
    // The Pause screen
    // ------------------------------------------------------------------

    /**
     * GAME PAUSED: the current day, time and balance, with RESUME and SAVE &
     * EXIT TO TITLE. Opened with the PAUSE button or the Esc key.
     */
    public static class PauseOverlay extends NotebookOverlay {

        /** What the two buttons do - supplied by RoomPanel. */
        public interface Actions {
            void onResume();

            void onSaveAndExit();
        }

        private final JLabel dayLabel;
        private final JLabel timeLabel;
        private final MoneyLabel balanceLabel = new MoneyLabel(16f, BALANCE_GREEN, MoneyLabel.Align.LEFT);
        private final WoodButton resumeButton;
        private final WoodButton exitButton;

        /** saveEnabled false (no save file available) labels the second button "EXIT TO TITLE" instead. */
        public PauseOverlay(Actions actions, boolean saveEnabled) {
            super("GAME PAUSED", STAMP_RED, STAMP_GOLD);
            dayLabel = addRow(0);
            timeLabel = addRow(1);

            JLabel balanceCaption = addRow(2);
            balanceCaption.setText("Balance:");
            balanceLabel.setBounds(TEXT_X + 150, rowY(2), 300, 34);
            add(balanceLabel);

            resumeButton = addBigButton("RESUME", WoodButton.GREEN, 0, actions::onResume);
            exitButton = addBigButton(saveEnabled ? "SAVE & EXIT TO TITLE" : "EXIT TO TITLE", WoodButton.RED, 1,
                    actions::onSaveAndExit);
        }

        /** Refreshes the three info rows. Call just before showing. */
        public void update(int day, int finalDay, TimeSlot slot, int cash) {
            dayLabel.setText("Current Day: Day " + day + " / " + finalDay);
            timeLabel.setText("Time: " + slot.getLabel());
            balanceLabel.setAmount(cash);
            balanceLabel.setColor(cash < 0 ? STAMP_RED : BALANCE_GREEN);
        }

        /** Puts keyboard focus on Resume, so Enter/Space resume and nothing in the room keeps focus. */
        public void focusResume() {
            resumeButton.requestFocusInWindow();
        }

        /** Temporarily disables both buttons (e.g. while a save is being written) so they can't be double-clicked. */
        public void setButtonsEnabled(boolean enabled) {
            resumeButton.setEnabled(enabled);
            exitButton.setEnabled(enabled);
        }
    }

    // ------------------------------------------------------------------
    // The Game Over screen
    // ------------------------------------------------------------------

    /**
     * GAME OVER, shown over the room the moment the player loses: why
     * ("You've gone broke!" or "You can't afford the ride home!"), the day,
     * the final balance with a BANKRUPT or STRANDED stamp, and RESTART /
     * EXIT GAME.
     */
    public static class GameOverOverlay extends NotebookOverlay {

        /** What the two buttons do - supplied by RoomPanel. */
        public interface Actions {
            void onRestart();

            void onExitGame();
        }

        private static final Color STAMP_SHADOW = new Color(0x5A, 0x1E, 0x18);

        private final JLabel reasonLabel;
        private final JLabel dayLabel;
        private String stampWord = "BANKRUPT";
        private final MoneyLabel balanceLabel = new MoneyLabel(16f, STAMP_RED, MoneyLabel.Align.LEFT);
        private final WoodButton restartButton;
        private final WoodButton exitButton;

        public GameOverOverlay(Actions actions) {
            super("GAME OVER", STAMP_RED, STAMP_RED);
            reasonLabel = addRow(0);
            reasonLabel.setText("You've gone broke!");
            dayLabel = addRow(1);
            addRow(2).setText("Balance:");
            balanceLabel.setBounds(TEXT_X + 150, rowY(2), 300, 34);
            add(balanceLabel);

            restartButton = addBigButton("RESTART", WoodButton.GREEN, 0, () -> {
                setButtonsEnabled(false);
                actions.onRestart();
                setButtonsEnabled(true); // still showing if the player backed out (e.g. kept their saves)
            });
            exitButton = addBigButton("EXIT GAME", WoodButton.RED, 1, actions::onExitGame);
        }

        /**
         * Fills in why the run ended, the day and the final balance. Call just
         * before showing. stranded: the player couldn't afford the fare home on
         * Day 30 (rather than going broke), shown as "STRANDED" instead of
         * "BANKRUPT".
         */
        public void update(int day, int finalDay, int cash, boolean stranded) {
            reasonLabel.setText(stranded ? "You can't afford the ride home!" : "You've gone broke!");
            stampWord = stranded ? "STRANDED" : "BANKRUPT";
            dayLabel.setText("Current Day: Day " + day + " / " + finalDay);
            balanceLabel.setAmount(cash);
        }

        public void focusRestart() {
            restartButton.requestFocusInWindow();
        }

        private void setButtonsEnabled(boolean enabled) {
            restartButton.setEnabled(enabled);
            exitButton.setEnabled(enabled);
        }

        /**
         * A tilted "BANKRUPT" (or "STRANDED") ink stamp at the right end of the
         * balance row, overlapping the page edge like a real stamp. Drawn at full
         * size with smoothing off; 16pt keeps the pixel font crisp.
         */
        @Override
        void paintExtraText(Graphics2D g2) {
            Font font = PixelKit.font(16f);
            FontMetrics fm = g2.getFontMetrics(font);
            String text = stampWord;
            int w = fm.stringWidth(text) + 24;
            int h = 36;
            int cx = PAPER.x + PAPER.width - w / 2 + 12; // hangs slightly off the page, clear of the balance
            int cy = rowY(2) + 17;

            AffineTransform saved = g2.getTransform();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g2.rotate(Math.toRadians(-10), cx, cy);
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(new Color(STAMP_RED.getRed(), STAMP_RED.getGreen(), STAMP_RED.getBlue(), 200));
            g2.drawRect(cx - w / 2, cy - h / 2, w, h);
            g2.setFont(font);
            g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
            g2.setTransform(saved);
        }

        /** The stamp's words get a darker red shadow instead of the default brown. */
        @Override
        void paintStampText(Graphics2D g2) {
            String text = "GAME OVER";
            Font font = PixelKit.font(32f);
            FontMetrics fm = g2.getFontMetrics(font);
            int textX = STAMP_X + (STAMP_W - fm.stringWidth(text)) / 2;
            int baseline = STAMP_Y + (STAMP_H + fm.getAscent() - fm.getDescent()) / 2;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g2.setFont(font);
            g2.setColor(STAMP_SHADOW);
            g2.drawString(text, textX + 4, baseline + 4);
            g2.setColor(STAMP_RED);
            g2.drawString(text, textX, baseline);
        }
    }
}
