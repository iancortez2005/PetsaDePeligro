package petsa.ui;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The game's pixel-art toolkit: everything the screens share for their look.
 *
 *   font()                         the pixel font (Press Start 2P, free under the SIL Open Font License)
 *   drawText() / wrapText() / ...  text in that font - the font has no peso sign, so every "₱"
 *                                  is drawn in a system font that has it, scaled to match
 *   paint() / snap() / SCALE       "pixelated" painting: shapes are drawn on a small image and scaled
 *                                  up, so every edge becomes chunky SCALE x SCALE blocks
 *   loadImage()                    pictures from src/petsa/ui/resources/ (cached)
 *   peso() / pesoCents()           peso amounts as text
 *
 * and the shared components, below: WoodButton, PaperCard, MoneyLabel,
 * TextBlock, BackgroundPanel, and FadePane (the quick fade between actions
 * and screens).
 */
public final class PixelKit {

    private PixelKit() {
        // static toolkit - not instantiable
    }

    // ------------------------------------------------------------------
    // The pixel font
    // ------------------------------------------------------------------

    private static final String FONT_RESOURCE_PATH = "resources/PressStart2P-Regular.ttf";

    private static Font baseFont;       // the loaded font at its default size, then derived per call
    private static boolean fontLoadAttempted = false;

    /** The pixel font at the given point size (a monospaced fallback if the font file is missing). */
    public static Font font(float size) {
        ensureFontLoaded();
        if (baseFont != null) {
            return baseFont.deriveFont(size);
        }
        return new Font(Font.MONOSPACED, Font.BOLD, Math.round(size));
    }

    private static void ensureFontLoaded() {
        if (fontLoadAttempted) {
            return;
        }
        fontLoadAttempted = true;
        try (InputStream in = PixelKit.class.getResourceAsStream(FONT_RESOURCE_PATH)) {
            if (in == null) {
                System.err.println("PixelKit: " + FONT_RESOURCE_PATH
                        + " not found on classpath - falling back to a monospaced font.");
                return;
            }
            Font loaded = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
            baseFont = loaded;
        } catch (IOException | FontFormatException e) {
            System.err.println("PixelKit: failed to load the pixel font (" + e.getMessage()
                    + ") - falling back to a monospaced font.");
        }
    }

    // ------------------------------------------------------------------
    // Pixel-font text (with the peso sign)
    // ------------------------------------------------------------------

    private static final char PESO = '₱';
    private static final String[] SYMBOL_FONT_CANDIDATES = {"Segoe UI", "Arial", "Tahoma", "Dialog", "SansSerif"};
    private static final float SYMBOL_SCALE = 1.35f; // system-font capitals are shorter than pixel-font glyphs of the same size
    private static final Map<Float, Font> SYMBOL_FONTS = new HashMap<>();
    private static final Graphics2D MEASURER = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();

    /** Width of text in the given pixel font, counting each peso sign at its system-font width. */
    public static int textWidth(String text, Font pixelFont) {
        FontMetrics pixel = MEASURER.getFontMetrics(pixelFont);
        Font symbolFont = symbolFontFor(pixelFont);
        int total = 0;
        for (String run : runs(text)) {
            if (run.charAt(0) == PESO) {
                total += (symbolFont != null)
                        ? (MEASURER.getFontMetrics(symbolFont).charWidth(PESO) + 2) * run.length()
                        : pixel.charWidth('P') * run.length();
            } else {
                total += pixel.stringWidth(run);
            }
        }
        return total;
    }

    /** Draws text with its left edge at x on the given baseline, in the Graphics' current colour. */
    public static void drawText(Graphics2D g, String text, Font pixelFont, int x, int baseline) {
        Font symbolFont = symbolFontFor(pixelFont);
        for (String run : runs(text)) {
            if (run.charAt(0) == PESO && symbolFont != null) {
                g.setFont(symbolFont);
                int step = g.getFontMetrics().charWidth(PESO) + 2;
                for (int i = 0; i < run.length(); i++) {
                    g.drawString(String.valueOf(PESO), x, baseline);
                    x += step;
                }
            } else {
                String drawn = (run.charAt(0) == PESO) ? run.replace(PESO, 'P') : run;
                g.setFont(pixelFont);
                g.drawString(drawn, x, baseline);
                x += g.getFontMetrics().stringWidth(drawn);
            }
        }
        g.setFont(pixelFont);
    }

    /** Splits text into lines no wider than maxWidth, breaking at spaces (a single over-long word gets its own line). */
    public static List<String> wrapText(String text, Font pixelFont, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && textWidth(candidate, pixelFont) > maxWidth) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** Splits text into alternating runs of peso signs and everything else. */
    private static List<String> runs(String text) {
        List<String> runs = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= text.length(); i++) {
            boolean boundary = i == text.length() || ((text.charAt(i) == PESO) != (text.charAt(i - 1) == PESO));
            if (boundary) {
                runs.add(text.substring(start, i));
                start = i;
            }
        }
        return runs;
    }

    /** A system font that has the peso sign, sized to sit level with the pixel font; null if no installed font has it. */
    private static Font symbolFontFor(Font pixelFont) {
        return SYMBOL_FONTS.computeIfAbsent(pixelFont.getSize2D(), size -> {
            int symbolSize = Math.round(size * SYMBOL_SCALE);
            for (String name : SYMBOL_FONT_CANDIDATES) {
                Font candidate = new Font(name, Font.BOLD, symbolSize);
                if (candidate.canDisplay(PESO)) {
                    return candidate;
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Pixelated painting
    // ------------------------------------------------------------------

    /** Screen pixels per art pixel. Raise for chunkier art; 1 turns the effect off. */
    public static final int SCALE = 3;

    /** Draws onto the low-resolution image, in full-size coordinates. */
    public interface Painter {
        void paint(Graphics2D g);
    }

    /** Rounds a size down to a whole number of art pixels, so a shape of that size ends exactly on a pixel edge. */
    public static int snap(int size) {
        return (size / SCALE) * SCALE;
    }

    /**
     * Paints the area (x, y, width, height) of target with painter, pixelated:
     * the painter draws in normal full-size coordinates onto an image SCALE
     * times smaller with anti-aliasing off, and that image is enlarged back
     * with nearest-neighbour scaling. Draw text afterwards at full size, so it
     * stays sharp. Size shapes with snap() so their edges line up, and prefer
     * filled shapes over outlines (fill the outer shape in the border colour,
     * then the inner shape on top).
     */
    public static void paint(Graphics2D target, int x, int y, int width, int height, Painter painter) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int lowWidth = (width + SCALE - 1) / SCALE;
        int lowHeight = (height + SCALE - 1) / SCALE;
        BufferedImage image = new BufferedImage(lowWidth, lowHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.scale(1.0 / SCALE, 1.0 / SCALE);
        g.translate(-x, -y);
        painter.paint(g);
        g.dispose();

        Object oldInterpolation = target.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        target.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // Center the art: any leftover (width - snap(width)) pixels are split evenly between both sides.
        int offsetX = (width - snap(width)) / 2;
        int offsetY = (height - snap(height)) / 2;
        target.drawImage(image, x + offsetX, y + offsetY, lowWidth * SCALE, lowHeight * SCALE, null);
        if (oldInterpolation != null) {
            target.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }

    // ------------------------------------------------------------------
    // Images and money text
    // ------------------------------------------------------------------

    private static final Map<String, Image> IMAGE_CACHE = new HashMap<>();

    /** Loads (and caches) one image from this package, e.g. "resources/menu_background.jpeg"; null if it doesn't exist. */
    static Image loadImage(String path) {
        if (IMAGE_CACHE.containsKey(path)) {
            return IMAGE_CACHE.get(path);
        }
        URL url = PixelKit.class.getResource(path);
        Image image = (url != null) ? new ImageIcon(url).getImage() : null;
        IMAGE_CACHE.put(path, image);
        return image;
    }

    /** A peso amount without centavos, e.g. 1500 -> "₱1,500". */
    static String peso(int amount) {
        return PESO + String.format("%,d", amount);
    }

    /** A peso amount with centavos and its sign in front, e.g. -70 -> "-₱70.00". */
    static String pesoCents(int amount) {
        return (amount < 0 ? "-" : "") + PESO + String.format("%,d.00", Math.abs(amount));
    }

    // ------------------------------------------------------------------
    // WoodButton
    // ------------------------------------------------------------------

    /**
     * The game's button, styled like the wooden signs in the room: a rounded
     * panel with a top-to-bottom gradient and a dark border, painted in pixel
     * art. It can show a title plus a smaller subtitle (like "BOARD" + "Daily
     * Tasks"), changes shade on hover and press, shrinks its text to fit, and
     * picks light text on dark colours and dark text on light ones.
     */
    public static class WoodButton extends JButton {

        public static final Color TAN = new Color(0xB8, 0x94, 0x66);
        public static final Color DARK_WOOD = new Color(0x5E, 0x44, 0x30);
        public static final Color GREEN = new Color(0x7C, 0x9B, 0x76);
        public static final Color BLUE = new Color(0x6F, 0x90, 0xAD);
        public static final Color RED = new Color(0xA8, 0x5C, 0x52);
        public static final Color ORANGE = new Color(0xD8, 0x86, 0x36);
        public static final Color YELLOW = new Color(0xD8, 0xB4, 0x3A);
        public static final Color PURPLE = new Color(0x7E, 0x4E, 0xA8);
        private static final Color DISABLED_GREY = new Color(0x6A, 0x62, 0x5A);

        private static final Color BORDER_COLOR = new Color(0x2A, 0x1D, 0x12);
        private static final Color LIGHT_TEXT = new Color(0xF4, 0xEA, 0xD6);
        private static final int ARC = 15; // a multiple of SCALE keeps the corners symmetric
        private static final int SIDE_PADDING = 10;
        private static final int LINE_GAP = 7;
        private static final float MIN_FONT_SIZE = 6f;

        private String title;
        private String subtitle;
        private Color baseColor;

        public WoodButton(String title, String subtitle, Color baseColor) {
            this.title = title;
            this.subtitle = subtitle;
            this.baseColor = baseColor;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(font(16f));
        }

        /** Changes the label and colour, e.g. for an ON / OFF toggle in Settings. */
        public void setTitleAndColor(String title, Color baseColor) {
            this.title = title;
            this.baseColor = baseColor;
            repaint();
        }

        public void setSubtitle(String subtitle) {
            this.subtitle = subtitle;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(220, 60);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            boolean enabled = isEnabled();
            boolean pressed = enabled && getModel().isPressed();
            boolean hover = enabled && getModel().isRollover();
            Color base = enabled ? baseColor : mix(baseColor, DISABLED_GREY, 0.7f);
            final Color top;
            final Color bottom;
            if (pressed) {
                top = shade(base, 0.70f);
                bottom = shade(base, 0.60f);
            } else if (hover) {
                top = shade(base, 1.25f);
                bottom = shade(base, 0.95f);
            } else {
                top = shade(base, 1.05f);
                bottom = shade(base, 0.75f);
            }

            // Body, border and bevel are pixelated; text is drawn sharp afterwards. The border is the outer
            // shape filled in the border colour, with the body filled on top one art pixel in - so every side
            // and corner is exactly the same thickness, with no gaps.
            int px = SCALE;
            int artW = snap(w);
            int artH = snap(h);
            PixelKit.paint(g2, 0, 0, w, h, pg -> {
                pg.setColor(BORDER_COLOR);
                pg.fill(new RoundRectangle2D.Float(0, 0, artW, artH, ARC, ARC));
                pg.setPaint(new GradientPaint(0, 0, top, 0, artH, bottom));
                pg.fill(new RoundRectangle2D.Float(px, px, artW - px * 2, artH - px * 2, ARC - px * 2, ARC - px * 2));
                pg.setColor(new Color(255, 255, 255, 55)); // highlight strip along the top, for a slight bevel
                pg.fillRect(px * 3, px * 2, artW - px * 6, px);
            });

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean darkBase = isDark(base);
            Color titleColor = darkBase ? LIGHT_TEXT : shade(base, 0.25f);
            Color subtitleColor = darkBase ? new Color(0xD9, 0xC7, 0xA8) : shade(base, 0.40f);
            if (!enabled) {
                titleColor = mix(titleColor, base, 0.45f);
                subtitleColor = mix(subtitleColor, base, 0.35f);
            }
            int maxTextWidth = w - SIDE_PADDING * 2;

            Font titleFont = fitFont(title, subtitle != null ? 14f : 16f, maxTextWidth);
            FontMetrics titleMetrics = g2.getFontMetrics(titleFont);

            if (subtitle == null) {
                int baseline = (h + titleMetrics.getAscent() - titleMetrics.getDescent()) / 2;
                drawCentered(g2, title, titleFont, titleColor, w, baseline);
            } else {
                Font subtitleFont = fitFont(subtitle, 9f, maxTextWidth);
                FontMetrics subtitleMetrics = g2.getFontMetrics(subtitleFont);
                int blockHeight = titleMetrics.getAscent() + LINE_GAP + subtitleMetrics.getAscent();
                int titleBaseline = (h - blockHeight) / 2 + titleMetrics.getAscent();
                int subtitleBaseline = titleBaseline + LINE_GAP + subtitleMetrics.getAscent();
                drawCentered(g2, title, titleFont, titleColor, w, titleBaseline);
                drawCentered(g2, subtitle, subtitleFont, subtitleColor, w, subtitleBaseline);
            }
            g2.dispose();
        }

        /** The largest pixel font, from preferredSize down, whose rendering of text fits within maxWidth. */
        private static Font fitFont(String text, float preferredSize, int maxWidth) {
            float size = preferredSize;
            Font fitted = font(size);
            while (size > MIN_FONT_SIZE && textWidth(text, fitted) > maxWidth) {
                size -= 1f;
                fitted = font(size);
            }
            return fitted;
        }

        private static void drawCentered(Graphics2D g2, String text, Font textFont, Color color, int width, int baseline) {
            g2.setColor(color);
            drawText(g2, text, textFont, (width - textWidth(text, textFont)) / 2, baseline);
        }

        private static boolean isDark(Color c) {
            double luminance = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
            return luminance < 0.45;
        }

        /** Blends a toward b; amount 0 = all a, 1 = all b. */
        private static Color mix(Color a, Color b, float amount) {
            return new Color(clamp(Math.round(a.getRed() + (b.getRed() - a.getRed()) * amount)),
                    clamp(Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * amount)),
                    clamp(Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * amount)));
        }

        private static Color shade(Color c, float factor) {
            return new Color(clamp((int) (c.getRed() * factor)), clamp((int) (c.getGreen() * factor)),
                    clamp((int) (c.getBlue() * factor)));
        }

        private static int clamp(int v) {
            return Math.max(0, Math.min(255, v));
        }
    }

    // ------------------------------------------------------------------
    // PaperCard
    // ------------------------------------------------------------------

    /**
     * A sheet of paper: a cream (or white) rectangle with a soft drop shadow
     * and a thin border, optionally ruled with a faint grid like graph paper.
     * Only a background - labels are added on top with setBounds.
     */
    public static class PaperCard extends JPanel {

        public static final Color CREAM = new Color(0xF1, 0xE6, 0xCF);
        public static final Color WHITE = new Color(0xF4, 0xF3, 0xEE);

        private static final int SHADOW = 6;
        private static final int GRID_SPACING = 24; // a multiple of SCALE keeps grid lines evenly spaced

        private final Color paperColor;
        private final boolean grid;

        public PaperCard(Color paperColor, boolean grid) {
            this.paperColor = paperColor;
            this.grid = grid;
            setLayout(null);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = snap(getWidth() - SHADOW);
            int h = snap(getHeight() - SHADOW);
            PixelKit.paint(g2, 0, 0, getWidth(), getHeight(), pg -> paintPaper(pg, w, h));
            g2.dispose();
        }

        private void paintPaper(Graphics2D g2, int w, int h) {
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fillRect(SHADOW, SHADOW, w, h);

            g2.setColor(paperColor);
            g2.fillRect(0, 0, w, h);

            if (grid) {
                g2.setColor(new Color(0x9C, 0xB4, 0xCC, 70));
                for (int x = GRID_SPACING; x < w; x += GRID_SPACING) {
                    g2.drawLine(x, 0, x, h);
                }
                for (int y = GRID_SPACING; y < h; y += GRID_SPACING) {
                    g2.drawLine(0, y, w, y);
                }
            }

            int px = SCALE;
            g2.setColor(new Color(0x8A, 0x74, 0x58));
            g2.fillRect(0, 0, w, px);           // top
            g2.fillRect(0, h - px, w, px);      // bottom
            g2.fillRect(0, 0, px, h);           // left
            g2.fillRect(w - px, 0, px, h);      // right
        }
    }

    // ------------------------------------------------------------------
    // MoneyLabel
    // ------------------------------------------------------------------

    /**
     * A peso amount like "₱4,930.00" (or "+₱500.00" / "-₱70.00"): the digits in
     * the pixel font and the peso sign in a system font that has it (see
     * symbolFontFor), or a plain "P" if no installed font does.
     */
    public static class MoneyLabel extends JComponent {

        public enum Align { LEFT, CENTER, RIGHT }

        private int amount;
        private boolean showPlusSign;
        private Color color;
        private final float fontSize;
        private final Align align;

        public MoneyLabel(float fontSize, Color color, Align align) {
            this.fontSize = fontSize;
            this.color = color;
            this.align = align;
            setOpaque(false);
        }

        /** Sets the amount. With showPlusSign, positive amounts get a leading "+" (for ledger rows like Salary Earned). */
        public void setAmount(int amount, boolean showPlusSign) {
            this.amount = amount;
            this.showPlusSign = showPlusSign;
            repaint();
        }

        public void setAmount(int amount) {
            setAmount(amount, false);
        }

        public void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            String sign = amount < 0 ? "-" : (showPlusSign && amount > 0 ? "+" : "");
            String digits = String.format("%,d.00", Math.abs(amount));

            Font pixelFont = font(fontSize);
            Font symbolFont = symbolFontFor(pixelFont);
            String symbol = symbolFont != null ? String.valueOf(PESO) : "P";
            Font symbolDrawFont = symbolFont != null ? symbolFont : pixelFont;

            FontMetrics pixelMetrics = g2.getFontMetrics(pixelFont);
            FontMetrics symbolMetrics = g2.getFontMetrics(symbolDrawFont);
            int signWidth = pixelMetrics.stringWidth(sign);
            int symbolWidth = symbolMetrics.stringWidth(symbol) + 2;
            int totalWidth = signWidth + symbolWidth + pixelMetrics.stringWidth(digits);

            int x = (align == Align.RIGHT) ? getWidth() - totalWidth
                    : (align == Align.CENTER) ? (getWidth() - totalWidth) / 2 : 0;
            int baseline = (getHeight() + pixelMetrics.getAscent() - pixelMetrics.getDescent()) / 2;

            g2.setColor(color);
            g2.setFont(pixelFont);
            g2.drawString(sign, x, baseline);
            x += signWidth;
            g2.setFont(symbolDrawFont);
            g2.drawString(symbol, x, baseline);
            x += symbolWidth;
            g2.setFont(pixelFont);
            g2.drawString(digits, x, baseline);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------
    // TextBlock
    // ------------------------------------------------------------------

    /**
     * A block of word-wrapped pixel-font text (peso signs supported), wrapped
     * to a fixed width when created. getBlockHeight() gives its height, to
     * size it and stack the next component below it.
     */
    public static class TextBlock extends JComponent {
        private final List<String> lines;
        private final Font font;
        private final Color color;
        private final List<Color> wordColors; // one per word, in order; null when the whole block is one colour
        private final int lineHeight;
        private final boolean centered;

        public TextBlock(String text, float fontSize, Color color, int width, int lineHeight, boolean centered) {
            this.font = font(fontSize);
            this.lines = wrapText(text, font, width);
            this.color = color;
            this.wordColors = null;
            this.lineHeight = lineHeight;
            this.centered = centered;
            setOpaque(false);
            setSize(width, getBlockHeight());
        }

        /**
         * Left-aligned text made of pieces that each keep their own colour
         * through the wrapping - e.g. a choice's good effects in green and its
         * bad ones in red on the same line. The pieces are joined with spaces.
         */
        public TextBlock(List<String> pieces, List<Color> pieceColors, float fontSize, int width, int lineHeight) {
            this.font = font(fontSize);
            this.wordColors = new ArrayList<>();
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < pieces.size(); i++) {
                for (String word : pieces.get(i).split(" ")) {
                    if (!word.isEmpty()) {
                        wordColors.add(pieceColors.get(i));
                        joined.append(word).append(' ');
                    }
                }
            }
            this.lines = wrapText(joined.toString().trim(), font, width);
            this.color = pieceColors.get(0);
            this.lineHeight = lineHeight;
            this.centered = false;
            setOpaque(false);
            setSize(width, getBlockHeight());
        }

        public int getBlockHeight() {
            return lines.size() * lineHeight;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(color);
            FontMetrics fm = g2.getFontMetrics(font);
            int baseline = (lineHeight + fm.getAscent() - fm.getDescent()) / 2;
            int wordIndex = 0;
            for (String line : lines) {
                int x = centered ? (getWidth() - textWidth(line, font)) / 2 : 0;
                if (wordColors == null) {
                    drawText(g2, line, font, x, baseline);
                    g2.setColor(color);
                } else {
                    for (String word : line.split(" ")) {
                        g2.setColor(wordColors.get(wordIndex++));
                        drawText(g2, word, font, x, baseline);
                        x += textWidth(word + " ", font);
                    }
                }
                baseline += lineHeight;
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------
    // BackgroundPanel
    // ------------------------------------------------------------------

    /**
     * A screen with a picture from resources/ stretched behind it - or a plain
     * dark gradient if none of the requested pictures exist, so a screen still
     * works before its art is added. It takes several candidate pictures and
     * uses the first that exists (e.g. the rainy room, falling back to the
     * normal one), and can switch pictures later with setBackgroundCandidates().
     * Real Swing components are placed on top with setBounds.
     */
    public static class BackgroundPanel extends JPanel {

        private static final Set<String> MISSING = new HashSet<>();

        private Image backgroundImage;

        /** Each path is relative to this package, e.g. "resources/menu_background.jpeg". The first one found is used. */
        public BackgroundPanel(String... candidatePaths) {
            setLayout(null); // children are positioned absolutely by whoever builds the screen
            setBackgroundCandidates(candidatePaths);
        }

        /** Switches to the first of these pictures that exists (or the placeholder gradient if none do) and repaints. */
        public final void setBackgroundCandidates(String... candidatePaths) {
            Image found = null;
            for (String path : candidatePaths) {
                found = loadImage(path);
                if (found != null) {
                    break;
                }
            }
            if (found == null && candidatePaths.length > 0) {
                String key = String.join("|", candidatePaths);
                if (MISSING.add(key)) {
                    System.err.println("BackgroundPanel: none of [" + String.join(", ", candidatePaths)
                            + "] found on classpath - using a placeholder gradient instead.");
                }
            }
            if (found != backgroundImage) {
                backgroundImage = found;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            if (backgroundImage != null) {
                g2.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
            } else {
                g2.setPaint(new GradientPaint(0, 0, new Color(0x1A, 0x14, 0x10), 0, getHeight(), new Color(0x0A, 0x08, 0x06)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }

    // ------------------------------------------------------------------
    // FadePane
    // ------------------------------------------------------------------

    /**
     * The quick fade-to-dark-and-back between actions and screens, installed
     * as the window's glass pane (the transparent layer Swing keeps above
     * everything else). A transition darkens the window, runs the action at
     * the darkest point, then fades back in; clicks are swallowed meanwhile,
     * so nothing can be double-clicked mid-transition.
     *
     * Usage: FadePane.install(frame) once, then wrap any action in
     * FadePane.run(someComponentInTheFrame, () -> ...). Without an installed
     * FadePane (or with fades turned off in Settings), run() just runs the
     * action immediately.
     */
    public static class FadePane extends JComponent {

        /** Quick dim used for ordinary button actions. */
        public static final float BUTTON_PEAK = 0.45f;
        public static final int BUTTON_OUT_MS = 90;
        public static final int BUTTON_IN_MS = 140;

        private static final int TICK_MS = 15;

        /** Settings > Screen Fades. When off, every action and screen change happens instantly. */
        private static boolean fadesEnabled = true;

        private float alpha = 0f;
        private boolean busy = false;
        private Timer timer;

        public FadePane() {
            setOpaque(false);
            setVisible(false);
            MouseAdapter swallowEverything = new MouseAdapter() { };
            addMouseListener(swallowEverything);
            addMouseMotionListener(swallowEverything);
            addMouseWheelListener(swallowEverything);
        }

        public static void setFadesEnabled(boolean enabled) {
            fadesEnabled = enabled;
        }

        /** Installs a FadePane as the frame's glass pane and returns it. */
        public static FadePane install(JFrame frame) {
            FadePane pane = new FadePane();
            frame.setGlassPane(pane);
            return pane;
        }

        /** Runs action inside the default quick button fade. */
        public static void run(Component source, Runnable action) {
            run(source, action, BUTTON_PEAK, BUTTON_OUT_MS, BUTTON_IN_MS);
        }

        /** Runs action inside a fade with a custom peak darkness (0-1) and fade-out/fade-in durations. */
        public static void run(Component source, Runnable action, float peak, int outMs, int inMs) {
            run(source, action, peak, outMs, inMs, null);
        }

        /** Like run(), plus onFinished (may be null), which runs once the fade-in has completed. */
        public static void run(Component source, Runnable action, float peak, int outMs, int inMs, Runnable onFinished) {
            JRootPane root = (source instanceof RootPaneContainer)
                    ? ((RootPaneContainer) source).getRootPane()
                    : SwingUtilities.getRootPane(source);
            if (!fadesEnabled || root == null || !(root.getGlassPane() instanceof FadePane)) {
                action.run();
                if (onFinished != null) {
                    onFinished.run();
                }
                return;
            }
            ((FadePane) root.getGlassPane()).transition(action, peak, outMs, inMs, onFinished);
        }

        /**
         * Fades out to peak, runs action, fades back in. If a transition is
         * already running (for example an action that itself switches
         * screens), the new action runs immediately inside the current one
         * instead of starting a second overlapping fade.
         */
        public void transition(Runnable action, float peak, int outMs, int inMs, Runnable onFinished) {
            if (busy) {
                action.run();
                if (onFinished != null) {
                    SwingUtilities.invokeLater(onFinished);
                }
                return;
            }
            busy = true;
            setVisible(true);
            animate(0f, peak, outMs, () -> SwingUtilities.invokeLater(() -> {
                try {
                    action.run();
                } finally {
                    animate(peak, 0f, inMs, () -> {
                        setVisible(false);
                        busy = false;
                        if (onFinished != null) {
                            onFinished.run();
                        }
                    });
                }
            }));
        }

        private void animate(float from, float to, int durationMs, Runnable onDone) {
            if (timer != null) {
                timer.stop();
            }
            long start = System.nanoTime();
            alpha = from;
            repaint();
            timer = new Timer(TICK_MS, null);
            timer.addActionListener(e -> {
                float progress = Math.min(1f, (System.nanoTime() - start) / 1_000_000f / Math.max(1, durationMs));
                alpha = from + (to - from) * progress;
                repaint();
                if (progress >= 1f) {
                    ((Timer) e.getSource()).stop();
                    onDone.run();
                }
            });
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (alpha > 0f) {
                g.setColor(new Color(0, 0, 0, Math.round(Math.min(1f, alpha) * 255)));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }
}
