package petsa.ui;

import petsa.ui.PixelKit.WoodButton;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * The game's own dialog box, used everywhere instead of Swing's plain
 * JOptionPane so every pop-up matches the pixel-art theme: a wooden
 * frame, a title bar, a paper panel with the message (word-wrapped,
 * peso signs supported), and WoodButtons.
 *
 * Four kinds, all modal (they wait for the player's answer):
 *   showMessage - a message with an OK button
 *   confirm     - a yes/no question (custom button labels)
 *   choose      - pick one of several options, or cancel
 *   askText     - type a short answer (e.g. a name)
 *
 * Esc cancels (closes a message, answers "no", picks nothing); Enter
 * presses the first button.
 */
public final class GameDialog {

    /** Sets the title bar's colour: gold for information, amber for warnings, red for errors. */
    public enum Tone { INFO, WARNING, ERROR }

    private static final int MIN_WIDTH = 540;
    private static final int PADDING = 30;
    private static final int TITLE_H = 45;
    private static final int LINE_H = 18;
    private static final int BUTTON_H = 48;
    private static final int BUTTON_GAP = 12;
    private static final int FIELD_H = 42;

    private static final Color WOOD_EDGE = new Color(0x2A, 0x1D, 0x12);
    private static final Color WOOD = new Color(0x6E, 0x4A, 0x2A);
    private static final Color TITLE_BAR = new Color(0x3A, 0x28, 0x1A);
    private static final Color PAPER = new Color(0xF1, 0xE6, 0xCF);
    private static final Color INK = new Color(0x2E, 0x24, 0x1C);

    private GameDialog() {
        // static helper - not instantiable
    }

    // ------------------------------------------------------------------
    // The four kinds of dialog
    // ------------------------------------------------------------------

    public static void showMessage(Component parent, String title, String message) {
        showMessage(parent, title, message, Tone.INFO);
    }

    public static void showMessage(Component parent, String title, String message, Tone tone) {
        show(parent, title, message, tone, new String[] {"OK"}, new Color[] {WoodButton.TAN}, 0, null);
    }

    /** A yes/no question. Returns true only if the player pressed yesLabel (Esc counts as no). */
    public static boolean confirm(Component parent, String title, String message, String yesLabel, String noLabel,
            Tone tone) {
        int answer = show(parent, title, message, tone, new String[] {yesLabel, noLabel},
                new Color[] {tone == Tone.INFO ? WoodButton.GREEN : WoodButton.RED, WoodButton.TAN}, 1, null);
        return answer == 0;
    }

    /**
     * Pick one of several options. options and colors line up; a cancel
     * button labelled cancelLabel is added last. Returns the index of the
     * option picked, or -1 if the player cancelled (or pressed Esc).
     */
    public static int choose(Component parent, String title, String message, String[] options, Color[] colors,
            String cancelLabel) {
        String[] labels = new String[options.length + 1];
        Color[] buttonColors = new Color[options.length + 1];
        System.arraycopy(options, 0, labels, 0, options.length);
        System.arraycopy(colors, 0, buttonColors, 0, options.length);
        labels[options.length] = cancelLabel;
        buttonColors[options.length] = WoodButton.TAN;
        int answer = show(parent, title, message, Tone.INFO, labels, buttonColors, options.length, null);
        return answer == options.length ? -1 : answer;
    }

    /** Asks for a short piece of text. Returns what was typed, or null if the player cancelled. */
    public static String askText(Component parent, String title, String message, String initialText) {
        JTextField field = new JTextField(initialText);
        field.setFont(PixelKit.font(12f));
        field.setForeground(INK);
        field.setBackground(new Color(0xFB, 0xF7, 0xEE));
        field.setCaretColor(INK);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(WOOD_EDGE, 3), BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        field.selectAll();
        int answer = show(parent, title, message, Tone.INFO, new String[] {"OK", "CANCEL"},
                new Color[] {WoodButton.GREEN, WoodButton.TAN}, 1, field);
        return answer == 0 ? field.getText() : null;
    }

    // ------------------------------------------------------------------
    // Building and showing the dialog
    // ------------------------------------------------------------------

    /** Shows the dialog and waits. Returns the index of the button pressed (cancelIndex for Esc / closing). */
    private static int show(Component parent, String title, String message, Tone tone, String[] labels,
            Color[] colors, int cancelIndex, JTextField field) {
        Window owner = parent == null ? null
                : (parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent));
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        int[] result = {cancelIndex};

        // Button widths: each fits its label (at least 150px); the dialog widens to fit the row if needed.
        Font buttonFont = PixelKit.font(14f);
        int[] widths = new int[labels.length];
        int rowWidth = 0;
        for (int i = 0; i < labels.length; i++) {
            widths[i] = Math.max(150, PixelKit.textWidth(labels[i], buttonFont) + 36);
            rowWidth += widths[i] + (i > 0 ? BUTTON_GAP : 0);
        }
        int width = PixelKit.snap(Math.max(MIN_WIDTH, rowWidth + PADDING * 2));
        Font messageFont = PixelKit.font(10f);
        List<String> lines = PixelKit.wrapText(message, messageFont, width - PADDING * 2);
        int messageTop = TITLE_H + 24;
        int fieldTop = messageTop + lines.size() * LINE_H + 12;
        int buttonTop = fieldTop + (field != null ? FIELD_H + 18 : 0) + 6;
        int height = PixelKit.snap(buttonTop + BUTTON_H + 24);

        DialogPanel panel = new DialogPanel(title, tone, lines, messageFont, messageTop);
        panel.setLayout(null);
        if (field != null) {
            field.setBounds(PADDING, fieldTop, width - PADDING * 2, FIELD_H);
            panel.add(field);
        }
        int x = (width - rowWidth) / 2;
        WoodButton first = null;
        for (int i = 0; i < labels.length; i++) {
            WoodButton button = new WoodButton(labels[i], null, colors[i]);
            button.setBounds(x, buttonTop, widths[i], BUTTON_H);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            int index = i;
            button.addActionListener(e -> {
                result[0] = index;
                dialog.dispose();
            });
            panel.add(button);
            if (first == null) {
                first = button;
            }
            x += widths[i] + BUTTON_GAP;
        }

        // Esc cancels; Enter presses the first button (in a text field, Enter submits it too).
        JComponent root = dialog.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        root.getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                result[0] = cancelIndex;
                dialog.dispose();
            }
        });
        dialog.getRootPane().setDefaultButton(first);

        dialog.setContentPane(panel);
        dialog.setSize(width, height);
        placeOver(dialog, owner);
        WoodButton focus = first;
        SwingUtilities.invokeLater(() -> {
            if (field != null) {
                field.requestFocusInWindow();
            } else if (focus != null) {
                focus.requestFocusInWindow();
            }
        });
        dialog.setVisible(true); // blocks until a button is pressed or Esc
        return result[0];
    }

    /**
     * Centers the dialog over its window. A minimized window (e.g. closed from
     * its taskbar preview) is restored first, so the question appears over the
     * game. The window's own bounds are used instead of setLocationRelativeTo,
     * which reads a minimized window's off-screen position (-25600, -25600 on
     * Windows) and pushes the dialog into the screen's top-left corner.
     */
    private static void placeOver(JDialog dialog, Window owner) {
        if (owner == null || !owner.isShowing()) {
            dialog.setLocationRelativeTo(null);
            return;
        }
        if (owner instanceof Frame) {
            Frame frame = (Frame) owner;
            if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) {
                frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
            }
        }
        Rectangle area = owner.getBounds();
        dialog.setLocation(area.x + (area.width - dialog.getWidth()) / 2,
                area.y + (area.height - dialog.getHeight()) / 2);
    }

    /** Paints the frame, title bar and paper; the message is drawn straight onto the paper. */
    private static class DialogPanel extends JPanel {
        private final String title;
        private final Tone tone;
        private final List<String> lines;
        private final Font messageFont;
        private final int messageTop;

        DialogPanel(String title, Tone tone, List<String> lines, Font messageFont, int messageTop) {
            this.title = title;
            this.tone = tone;
            this.lines = lines;
            this.messageFont = messageFont;
            this.messageTop = messageTop;
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            PixelKit.paint(g2, 0, 0, w, h, pg -> {
                int px = PixelKit.SCALE;
                int artW = PixelKit.snap(w);
                int artH = PixelKit.snap(h);
                pg.setColor(WOOD_EDGE);
                pg.fillRect(0, 0, artW, artH);
                pg.setColor(WOOD);
                pg.fillRect(px, px, artW - px * 2, artH - px * 2);
                pg.setColor(TITLE_BAR);
                pg.fillRect(px * 3, px * 3, artW - px * 6, TITLE_H - px * 3);
                pg.setColor(WOOD_EDGE);
                pg.fillRect(px * 3, TITLE_H, artW - px * 6, artH - TITLE_H - px * 3);
                pg.setColor(PAPER);
                pg.fillRect(px * 4, TITLE_H + px, artW - px * 8, artH - TITLE_H - px * 5);
            });

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            Font titleFont = PixelKit.font(16f);
            FontMetrics fm = g2.getFontMetrics(titleFont);
            g2.setColor(titleColor());
            String shown = title.toUpperCase();
            PixelKit.drawText(g2, shown, titleFont, PADDING - 6, (TITLE_H + fm.getAscent() - fm.getDescent()) / 2 + 3);

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics mfm = g2.getFontMetrics(messageFont);
            int baseline = messageTop + mfm.getAscent();
            for (String line : lines) {
                g2.setColor(INK);
                PixelKit.drawText(g2, line, messageFont, PADDING, baseline);
                baseline += LINE_H;
            }
            g2.dispose();
        }

        private Color titleColor() {
            switch (tone) {
                case WARNING:
                    return new Color(0xF0, 0xA8, 0x48);
                case ERROR:
                    return new Color(0xF0, 0x6A, 0x5A);
                default:
                    return new Color(0xE8, 0xC2, 0x4A);
            }
        }
    }
}
